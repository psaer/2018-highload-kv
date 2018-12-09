package ru.mail.polis.psaer.requestHandlers;

import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.pool.PoolException;
import org.apache.log4j.Logger;
import org.iq80.leveldb.DBException;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.psaer.domain.DaoValue;
import ru.mail.polis.psaer.dto.ReplicaAnswerResultDTO;
import ru.mail.polis.psaer.dto.ReplicaAnswerResultsDTO;
import ru.mail.polis.psaer.dto.RequestHandleDTO;
import ru.mail.polis.psaer.exceptions.ReplicaParamsException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.mail.polis.psaer.Constants.*;

public class DeleteHandler extends AbstractHandler {

    @NotNull
    private final Logger logger;

    @NotNull
    private Integer successAnswers;

    public DeleteHandler(@NotNull RequestHandleDTO requestHandleDTO) throws ReplicaParamsException {
        super(requestHandleDTO);
        this.logger = Logger.getLogger(DeleteHandler.class);
        this.successAnswers = 0;
    }

    @Override
    public void processQueryFromReplica() throws IOException {
        try {
            internalRemove(getRequestTimestamp());
            httpSession.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        } catch (DBException ex) {
            logger.error(ex.getMessage(), ex);
            httpSession.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    @Override
    public void processQueryFromClient() throws IOException {
        if (replicasHosts.isEmpty() || replicaParamsDTO.getFrom() == 1) {
            singleNodeHandle();
        } else {
            multipleNodeHandle();
        }
    }

    private void internalRemove(long timestamp) throws IOException, DBException {
        DaoValue daoValue = new DaoValue(DaoValue.State.REMOVED, timestamp, new byte[]{});
        dao.internalUpsert(id.getBytes(), daoValue);
    }

    private void singleNodeHandle() throws IOException {
        try {
            dao.remove(id.getBytes());
            httpSession.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        } catch (Exception e) {
            logger.error("Can't handle DELETE request", e);
            httpSession.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    private void multipleNodeHandle() throws IOException {

        final long timestamp = getCurrentTimestamp();
        List<ReplicaAnswerResultDTO> results = new ArrayList<>();
        results.add(removeFromCurrentNode(timestamp));

        for (Map.Entry<String, HttpClient> replicaEntry : replicasHosts.entrySet()) {
            if (this.successAnswers >= this.replicaParamsDTO.getFrom()) {
                break;
            }

            ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(replicaEntry.getKey());

            String[] headers = new String[2];
            headers[0] = HEADER_VALUE_TIMESTAMP + timestamp;
            headers[1] = HEADER_REPLICA_REQUEST + true;

            try {
                Response response = replicaEntry.getValue().delete(
                        request.getURI(),
                        headers
                );

                result.workingReplica();

                switch (response.getStatus()) {
                    case STATUS_SUCCESS_DELETE:
                        result.successOperation();
                        this.successAnswers++;
                        break;
                }
            } catch (InterruptedException | HttpException | IOException | PoolException e) {
                logger.error(e.getMessage(), e);
            } finally {
                results.add(result);
            }
        }

        ReplicaAnswerResultsDTO replicaAnswerResultsDTO = new ReplicaAnswerResultsDTO(results);

        if (replicaAnswerResultsDTO.getWorkingReplicas() < replicaParamsDTO.getAck()) {
            httpSession.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        } else {
            httpSession.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        }
    }

    private ReplicaAnswerResultDTO removeFromCurrentNode(long timestamp) {
        ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(myReplicaHost);

        try {
            internalRemove(timestamp);

            result.workingReplica();
            result.successOperation();
            this.successAnswers++;
        } catch (IOException e) {
            result.badArgument();
            logger.error(e.getMessage(), e);
        } catch (DBException e) {
            logger.error(e.getMessage(), e);
        }

        return result;
    }
}

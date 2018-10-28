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

import static ru.mail.polis.psaer.Constants.*;

public class PutHandler extends AbstractHandler {

    @NotNull
    private final Logger logger;

    @NotNull
    private Integer successAnswers;

    public PutHandler(@NotNull RequestHandleDTO requestHandleDTO) throws ReplicaParamsException {
        super(requestHandleDTO);
        this.logger = Logger.getLogger(PutHandler.class);
        this.successAnswers = 0;
    }

    @Override
    public void processQueryFromReplica() throws IOException {
        try {
            internalUpsert(getRequestTimestamp());
            httpSession.sendResponse(new Response(Response.CREATED, Response.EMPTY));
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

    private void internalUpsert(long timestamp) throws IOException, DBException {
        DaoValue daoValue = new DaoValue(DaoValue.State.PRESENT, timestamp, request.getBody());
        dao.internalUpsert(id.getBytes(), daoValue);
    }

    private void singleNodeHandle() throws IOException {
        try {
            dao.upsert(id.getBytes(), request.getBody());
            httpSession.sendResponse(new Response(Response.CREATED, Response.EMPTY));
        } catch (DBException ex) {
            logger.error(ex.getMessage(), ex);
            httpSession.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    private void multipleNodeHandle() throws IOException {
        final long timestamp = getCurrentTimestamp();

        List<ReplicaAnswerResultDTO> results = new ArrayList<>();
        results.add(upsertIntoCurrentNode(timestamp));

        for (String replicaHost : replicasHosts) {
            if (this.successAnswers >= this.replicaParamsDTO.getFrom()) {
                break;
            }

            ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(replicaHost);

            HttpClient httpClient = new HttpClient(
                    new ConnectionString(replicaHost)
            );

            String[] headers = new String[2];
            headers[0] = HEADER_VALUE_TIMESTAMP + timestamp;
            headers[1] = HEADER_REPLICA_REQUEST + true;

            try {
                Response response = httpClient.put(
                        request.getURI(),
                        request.getBody(),
                        headers
                );

                result.workingReplica();

                switch (response.getStatus()) {
                    case STATUS_SUCCESS_PUT:
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

        if (replicaAnswerResultsDTO.getSuccessOperations() < replicaParamsDTO.getAck()) {
            httpSession.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        } else {
            httpSession.sendResponse(new Response(Response.CREATED, Response.EMPTY));
        }
    }

    private ReplicaAnswerResultDTO upsertIntoCurrentNode(long timestamp) {
        ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(myReplicaHost);

        try {
            internalUpsert(timestamp);

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

package ru.mail.polis.psaer.requestHandlers;

import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.Response;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static ru.mail.polis.psaer.Constants.*;

public class PutHandler extends AbstractHandler {

    @NotNull
    private final Logger logger;

    @NotNull
    private Integer answers;

    public PutHandler(@NotNull RequestHandleDTO requestHandleDTO) throws ReplicaParamsException {
        super(requestHandleDTO);
        this.logger = Logger.getLogger(PutHandler.class);
        this.answers = 0;
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

        List<Future<ReplicaAnswerResultDTO>> futureNodeAnswers = requestForFutureWork(timestamp);
        for (Future<ReplicaAnswerResultDTO> replicaAnswerResultDTOFuture : futureNodeAnswers) {
            try {
                results.add(replicaAnswerResultDTOFuture.get());
            }catch (Exception ex){
                logger.error(ex.getMessage(), ex);
            }
        }

        ReplicaAnswerResultsDTO replicaAnswerResultsDTO = new ReplicaAnswerResultsDTO(results);
        if (replicaAnswerResultsDTO.getSuccessOperations() < replicaParamsDTO.getAck()) {
            httpSession.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        } else {
            httpSession.sendResponse(new Response(Response.CREATED, Response.EMPTY));
        }
    }

    private List<Future<ReplicaAnswerResultDTO>> requestForFutureWork(long timestamp){
        List<Future<ReplicaAnswerResultDTO>> answerList = new LinkedList<>();
        for (Map.Entry<String, HttpClient> replicaEntry : replicasHosts.entrySet()) {
            if (this.answers >= this.replicaParamsDTO.getFrom()) {
                break;
            }

            String[] headers = new String[2];
            headers[0] = HEADER_VALUE_TIMESTAMP + timestamp;
            headers[1] = HEADER_REPLICA_REQUEST + true;

            Future<ReplicaAnswerResultDTO> replicaAnswerResultDTOFuture = threadPool.submit(() ->{
                ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(replicaEntry.getKey());

                try {
                    Response response = replicaEntry.getValue().put(
                            request.getURI(),
                            request.getBody(),
                            headers
                    );

                    result.workingReplica();

                    switch (response.getStatus()) {
                        case STATUS_SUCCESS_PUT:
                            result.successOperation();
                            break;
                    }
                } catch (InterruptedException | HttpException | IOException | PoolException e) {
                    logger.error(e.getMessage(), e);
                }
                return result;
            });
            answerList.add(replicaAnswerResultDTOFuture);
            this.answers++;
        }
        return answerList;
    }

    private ReplicaAnswerResultDTO upsertIntoCurrentNode(long timestamp) {
        ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(myReplicaHost);

        try {
            internalUpsert(timestamp);

            result.workingReplica();
            result.successOperation();
            this.answers++;
        } catch (IOException e) {
            result.badArgument();
            logger.error(e.getMessage(), e);
        } catch (DBException e) {
            logger.error(e.getMessage(), e);
        }

        return result;
    }
}

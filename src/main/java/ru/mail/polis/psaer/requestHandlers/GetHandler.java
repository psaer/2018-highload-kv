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
import java.util.*;
import java.util.concurrent.Future;

import static ru.mail.polis.psaer.Constants.*;

public class GetHandler extends AbstractHandler {

    @NotNull
    private final Logger logger;

    @NotNull
    private Integer answers;

    private static String[] replicaRequestHeaders;

    public GetHandler(@NotNull RequestHandleDTO requestHandleDTO) throws ReplicaParamsException {
        super(requestHandleDTO);
        this.logger = Logger.getLogger(GetHandler.class);
        this.answers = 0;

        this.replicaRequestHeaders = new String[1];
        this.replicaRequestHeaders[0] = HEADER_REPLICA_REQUEST + true;
    }

    @Override
    public void processQueryFromReplica() throws IOException {
        try {
            DaoValue daoValue = dao.internalGet(id.getBytes());

            Response response = new Response(Response.OK, Response.EMPTY);
            if (isValueNeeded()) {
                response = Response.ok(daoValue.getData());
            }
            if (daoValue.getState() == DaoValue.State.REMOVED) {
                response.addHeader(HEADER_VALUE_REMOVED + true);
            }
            response.addHeader(HEADER_VALUE_TIMESTAMP + daoValue.getTimestamp());
            response.addHeader(HEADER_VALUE_SIZE + String.valueOf(daoValue.getData().length));

            httpSession.sendResponse(response);
        } catch (NoSuchElementException e) {
            Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
            response.addHeader(HEADER_VALUE_TIMESTAMP + 0);
            response.addHeader(HEADER_VALUE_SIZE + 0);

            httpSession.sendResponse(response);
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

    private void singleNodeHandle() throws IOException {
        try {
            final byte[] value = dao.get(id.getBytes());
            httpSession.sendResponse(Response.ok(value));
        } catch (NoSuchElementException e) {
            httpSession.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
        } catch (Exception e) {
            logger.error("Can't handle GET request", e);
            httpSession.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    private void multipleNodeHandle() throws IOException {
        List<ReplicaAnswerResultDTO> results = new ArrayList<>();
        ReplicaAnswerResultDTO currentNodeResult = getFromCurrentNode(id.getBytes());
        Long currentNodeTimestamp = currentNodeResult.getValueTimestamp();
        results.add(currentNodeResult);

        List<Future<ReplicaAnswerResultDTO>> futureNodeAnswers = requestForFutureWork();
        for (Future<ReplicaAnswerResultDTO> replicaAnswerResultDTOFuture : futureNodeAnswers) {
            try {
                results.add(replicaAnswerResultDTOFuture.get());
            }catch (Exception ex){
                logger.error(ex.getMessage(), ex);
            }
        }

        ReplicaAnswerResultsDTO replicaAnswerResultsDTO = new ReplicaAnswerResultsDTO(results);

        if (replicaAnswerResultsDTO.getWorkingReplicas() < replicaParamsDTO.getAck()) {
            httpSession.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        } else {
            chooseValueAndResponse(results, currentNodeTimestamp);
        }
    }

    private List<Future<ReplicaAnswerResultDTO>> requestForFutureWork(){
        List<Future<ReplicaAnswerResultDTO>> answerList = new LinkedList<>();
        for (Map.Entry<String, HttpClient> replicaEntry : replicasHosts.entrySet()) {
            if (this.answers >= this.replicaParamsDTO.getFrom()) {
                break;
            }

            Future<ReplicaAnswerResultDTO> replicaAnswerResultDTOFuture = threadPool.submit(() ->{
                ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(replicaEntry.getKey());

                try {
                    Response response = replicaEntry.getValue().get(
                            request.getURI(),
                            replicaRequestHeaders
                    );

                    result.workingReplica();
                    result.successOperation();

                    switch (response.getStatus()) {
                        case STATUS_OK:
                            result.setValueTimestamp(getTimestampFromResponse(response));
                            result.setDeleted(isValueRemoved(response));
                            break;
                        case STATUS_BAD_ARGUMENT:
                            result.badArgument();
                            break;
                        case STATUS_NOT_FOUND:
                            result.notFound();
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

    private void chooseValueAndResponse(List<ReplicaAnswerResultDTO> results, Long currentNodeTimestamp) throws IOException {
        results.sort(Comparator.comparingLong(ReplicaAnswerResultDTO::getValueTimestamp));
        ReplicaAnswerResultDTO bestResult = results.get(results.size() - 1);

        if (bestResult.isDeleted()) {
            httpSession.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            return;
        }

        if (bestResult.getValueTimestamp() == currentNodeTimestamp) {
            singleNodeHandle();
            return;
        } else {
            String[] headers = new String[2];
            headers[0] = HEADER_REPLICA_REQUEST + true;
            headers[1] = HEADER_REPLICA_REQUEST_FOR_VALUE + true;

            Response response = null;
            try {
                response = replicasHosts.get(bestResult.getReplicaHost()).get(
                        request.getURI(),
                        headers
                );

                switch (response.getStatus()) {
                    case STATUS_OK:
                        httpSession.sendResponse(Response.ok(response.getBody()));
                        return;
                }

            } catch (InterruptedException | HttpException | IOException | PoolException e) {
                logger.error(e.getMessage(), e);
            }
        }

        httpSession.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
    }

    private ReplicaAnswerResultDTO getFromCurrentNode(byte[] id) {
        ReplicaAnswerResultDTO result = new ReplicaAnswerResultDTO(myReplicaHost);

        try {
            DaoValue daoValue = dao.internalGet(id);

            result.workingReplica();
            result.successOperation();
            result.setValueTimestamp(daoValue.getTimestamp());
            if (daoValue.getState() == DaoValue.State.REMOVED) {
                result.setDeleted(true);
            }
            this.answers++;
        } catch (NoSuchElementException e) {
            result.workingReplica();
            result.successOperation();
            result.notFound();
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

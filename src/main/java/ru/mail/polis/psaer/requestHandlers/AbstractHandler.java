package ru.mail.polis.psaer.requestHandlers;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.psaer.Constants;
import ru.mail.polis.psaer.KVDaoImpl;
import ru.mail.polis.psaer.dto.ReplicaParamsDTO;
import ru.mail.polis.psaer.dto.RequestHandleDTO;
import ru.mail.polis.psaer.exceptions.ReplicaParamsException;
import ru.mail.polis.psaer.service.QueryParamsService;

import java.io.IOException;
import java.util.*;

public abstract class AbstractHandler {

    @NotNull
    protected KVDaoImpl dao;

    @NotNull
    protected String myReplicaHost;

    @NotNull
    protected Set<String> replicasHosts;

    @NotNull
    protected Request request;

    @NotNull
    protected HttpSession httpSession;

    @NotNull
    protected final String id;

    @NotNull
    protected final ReplicaParamsDTO replicaParamsDTO;

    protected final boolean requestFromReplica;

    public AbstractHandler(@NotNull RequestHandleDTO requestHandleDTO) throws ReplicaParamsException {
        this.dao = requestHandleDTO.getDao();
        this.myReplicaHost = requestHandleDTO.getMyReplicaHost();
        this.replicasHosts = requestHandleDTO.getReplicasHosts();
        this.request = requestHandleDTO.getRequest();
        this.httpSession = requestHandleDTO.getHttpSession();

        QueryParamsService queryParamsService = new QueryParamsService(this.request);
        this.id = queryParamsService.getId();
        this.replicaParamsDTO = queryParamsService.getReplicaParams(this.replicasHosts.size() + 1);

        this.requestFromReplica = isReplicaRequest();
    }

    public void handle() throws IOException {
        if (requestFromReplica) {
            processQueryFromReplica();
        } else {
            processQueryFromClient();
        }
    }

    private boolean isReplicaRequest() {
        return Boolean.valueOf(request.getHeader(Constants.HEADER_REPLICA_REQUEST));
    }

    protected long getRequestTimestamp() {
        String timestampString = request.getHeader(Constants.HEADER_VALUE_TIMESTAMP);
        return timestampString == null ? -1 : Long.valueOf(timestampString);
    }

    protected long getTimestampFromResponse(@NotNull Response response) {
        String timestampString = response.getHeader(Constants.HEADER_VALUE_TIMESTAMP);
        return timestampString == null ? -1 : Long.valueOf(timestampString);
    }

    protected boolean isValueRemoved(@NotNull Response response) {
        return Boolean.valueOf(response.getHeader(Constants.HEADER_VALUE_REMOVED));
    }

    protected boolean isValueNeeded() {
        return Boolean.valueOf(request.getHeader(Constants.HEADER_REPLICA_REQUEST_FOR_VALUE));
    }

    protected long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    public abstract void processQueryFromReplica() throws IOException;

    public abstract void processQueryFromClient() throws IOException;

}

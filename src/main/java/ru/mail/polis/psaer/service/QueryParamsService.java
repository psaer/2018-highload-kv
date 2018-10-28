package ru.mail.polis.psaer.service;

import one.nio.http.Request;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.mail.polis.psaer.dto.ReplicaParamsDTO;
import ru.mail.polis.psaer.exceptions.ReplicaParamsException;

import static ru.mail.polis.psaer.Constants.EMPTY_KEY;

public class QueryParamsService {

    @Nullable
    private String id = null;

    @Nullable
    private ReplicaParamsDTO replicaParamsDTO = null;

    public QueryParamsService(@NotNull Request request) {
        request.getParameters(EMPTY_KEY).forEachRemaining(param -> {
            String[] splittedParam = param.split("=");

            switch (splittedParam[0]) {
                case "id":
                    if (splittedParam.length == 1) {
                        throw new IllegalArgumentException("Id key is null");
                    }
                    id = splittedParam[1];
                    break;
                case "replicas":
                    replicaParamsDTO = new ReplicaParamsDTO(splittedParam[1]);
                    break;
            }
        });
    }

    @NotNull
    public String getId() {
        if (id == null) {
            throw new IllegalArgumentException();
        }
        return id;
    }

    @NotNull
    public ReplicaParamsDTO getReplicaParams(int replicasCount) throws ReplicaParamsException {
        if (replicaParamsDTO == null) {
            return new ReplicaParamsDTO(replicasCount / 2 + 1, replicasCount);
        } else if (
                replicaParamsDTO.getFrom() > replicasCount ||
                        replicaParamsDTO.getAck() > replicaParamsDTO.getFrom() ||
                        replicaParamsDTO.getAck() < 1) {
            throw new ReplicaParamsException("Invalid params. Ack: " + replicaParamsDTO.getAck() + ", from: " + replicaParamsDTO.getFrom());
        } else {
            return replicaParamsDTO;
        }
    }
}

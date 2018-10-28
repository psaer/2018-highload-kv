package ru.mail.polis.psaer.dto;

import org.jetbrains.annotations.NotNull;

public class ReplicaAnswerResultDTO {

    @NotNull
    private final String replicaHost;

    private boolean workingReplica = false;
    private boolean notFound = false;
    private boolean successOperation = false;
    private boolean badArgument = false;
    private boolean deleted = false;
    private long valueTimestamp = -1;

    public ReplicaAnswerResultDTO(@NotNull String replicaHost) {
        this.replicaHost = replicaHost;
    }

    public void notFound() {
        notFound = true;
    }

    public void workingReplica() {
        workingReplica = true;
    }

    public void successOperation() {
        successOperation = true;
    }

    public void setValueTimestamp(long timestamp) {
        valueTimestamp = timestamp;
    }

    public void badArgument() {
        badArgument = true;
    }

    @NotNull
    public String getReplicaHost() {
        return replicaHost;
    }

    public boolean isWorkingReplica() {
        return workingReplica;
    }

    public boolean isNotFound() {
        return notFound;
    }

    public boolean isSuccessOperation() {
        return successOperation;
    }

    public boolean isBadArgument() {
        return badArgument;
    }

    public long getValueTimestamp() {
        return valueTimestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}

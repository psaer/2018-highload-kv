package ru.mail.polis.psaer.dto;

import java.util.*;

public class ReplicaAnswerResultsDTO {

    private boolean badArgument = false;
    private int notFound = 0;
    private int workingReplicas = 0;
    private int successOperations = 0;

    public ReplicaAnswerResultsDTO(List<ReplicaAnswerResultDTO> results) {
        for (ReplicaAnswerResultDTO result : results) {
            badArgument = badArgument || result.isBadArgument();

            if (result.isNotFound()) {
                notFound++;
            }

            if (result.isWorkingReplica()) {
                workingReplicas++;
            }

            if (result.isSuccessOperation()) {
                successOperations++;
            }

        }
    }

    public int getWorkingReplicas() {
        return workingReplicas;
    }

    public int getSuccessOperations() {
        return successOperations;
    }

}

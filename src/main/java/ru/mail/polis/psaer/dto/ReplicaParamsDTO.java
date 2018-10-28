package ru.mail.polis.psaer.dto;

import org.jetbrains.annotations.NotNull;

public class ReplicaParamsDTO {

    private int ack;
    private int from;

    public ReplicaParamsDTO(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public ReplicaParamsDTO(@NotNull String parameters) {
        String[] parameter = parameters.split("/");

        ack = Integer.parseInt(parameter[0]);
        from = Integer.parseInt(parameter[1]);
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }
}
package ru.mail.polis.psaer.dto;

import one.nio.http.HttpClient;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.psaer.KVDaoImpl;

import java.util.Map;

public class RequestHandleDTO {

    @NotNull
    private KVDaoImpl dao;

    @NotNull
    private String myReplicaHost;

    @NotNull
    private Map<String, HttpClient> replicasHosts;

    @NotNull
    private Request request;

    @NotNull
    private HttpSession httpSession;

    @NotNull
    public KVDaoImpl getDao() {
        return dao;
    }

    public RequestHandleDTO setDao(@NotNull KVDaoImpl dao) {
        this.dao = dao;
        return this;
    }

    @NotNull
    public String getMyReplicaHost() {
        return myReplicaHost;
    }

    public RequestHandleDTO setMyReplicaHost(@NotNull String myReplicaHost) {
        this.myReplicaHost = myReplicaHost;
        return this;
    }

    @NotNull
    public Map<String, HttpClient>getReplicasHosts() {
        return replicasHosts;
    }

    public RequestHandleDTO setReplicasHosts(@NotNull Map<String, HttpClient>replicasHosts) {
        this.replicasHosts = replicasHosts;
        return this;
    }

    @NotNull
    public Request getRequest() {
        return request;
    }

    public RequestHandleDTO setRequest(@NotNull Request request) {
        this.request = request;
        return this;
    }

    @NotNull
    public HttpSession getHttpSession() {
        return httpSession;
    }

    public RequestHandleDTO setHttpSession(@NotNull HttpSession httpSession) {
        this.httpSession = httpSession;
        return this;
    }

}

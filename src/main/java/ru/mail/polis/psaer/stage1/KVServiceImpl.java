package ru.mail.polis.psaer.stage1;

import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVDao;
import ru.mail.polis.KVService;

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Implementation of {@link KVService} interface using one-nio http server
 *
 * @author Denis Kruminsh <ipsaer@gmail.com>
 */
public class KVServiceImpl extends HttpServer implements KVService {

    private static final String INTERNAL_ERROR_MESSAGE = "Server problem";

    private static final String DEFAULT_PATH = "/v0/entity";
    private static final String STATUS_POINT = "/v0/status";

    @NotNull
    private final Logger logger;

    @NotNull
    private final KVDao dao;

    public KVServiceImpl(
            final int port,
            @NotNull final KVDao dao) throws IOException {
        super(from(port));
        this.dao = dao;
        this.logger = Logger.getLogger(KVServiceImpl.class);
    }

    @Path(STATUS_POINT)
    public Response status() {
        return Response.ok("I'm OK");
    }

    @Override
    public void handleDefault(
            @NotNull final Request request,
            @NotNull final HttpSession session
    ) throws IOException {
        if (!request.getPath().equals(DEFAULT_PATH)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        final String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        switch (request.getMethod()) {
            case Request.METHOD_GET:
                handleGetRequest(id, session);
                break;
            case Request.METHOD_PUT:
                handlePutRequest(id, request, session);
                break;
            case Request.METHOD_DELETE:
                handleDeleteRequest(id, session);
                break;
            default:
                session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private void handleGetRequest(String id, HttpSession session) throws IOException {
        try {
            final byte[] value = dao.get(id.getBytes());
            session.sendResponse(Response.ok(value));
        } catch (NoSuchElementException e) {
            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
        } catch (Exception e) {
            logger.error("Can't handle GET request", e);
            session.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    private void handlePutRequest(String id, Request request, HttpSession session) throws IOException {
        try {
            dao.upsert(id.getBytes(), request.getBody());
            session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
        } catch (Exception e) {
            logger.error("Can't handle PUT request", e);
            session.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    private void handleDeleteRequest(String id, HttpSession session) throws IOException {
        try {
            dao.remove(id.getBytes());
            session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        } catch (Exception e) {
            logger.error("Can't handle DELETE request", e);
            session.sendResponse(new Response(Response.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE.getBytes()));
        }
    }

    private static HttpServerConfig from(final int port) {
        final AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;

        HttpServerConfig config = new HttpServerConfig();
        config.acceptors = new AcceptorConfig[]{ac};
        return config;
    }
}

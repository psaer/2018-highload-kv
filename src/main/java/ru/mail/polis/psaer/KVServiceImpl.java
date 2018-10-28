package ru.mail.polis.psaer;

import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVDao;
import ru.mail.polis.KVService;
import ru.mail.polis.psaer.dto.RequestHandleDTO;
import ru.mail.polis.psaer.exceptions.ReplicaParamsException;
import ru.mail.polis.psaer.requestHandlers.AbstractHandler;
import ru.mail.polis.psaer.requestHandlers.DeleteHandler;
import ru.mail.polis.psaer.requestHandlers.GetHandler;
import ru.mail.polis.psaer.requestHandlers.PutHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of {@link KVService} interface using one-nio http server
 *
 * @author Denis Kruminsh <ipsaer@gmail.com>
 */
public class KVServiceImpl extends HttpServer implements KVService {

    private static final String DEFAULT_PATH = "/v0/entity";
    private static final String STATUS_POINT = "/v0/status";

    private static final String REPLICA_PATH = "http://localhost:";

    @NotNull
    private final String myReplicaHost;

    @NotNull
    private Set<String> replicas;

    @NotNull
    private final Logger logger;

    @NotNull
    private final KVDaoImpl dao;

    public KVServiceImpl(
            final int port,
            @NotNull final KVDao dao,
            @NotNull Set<String> replicas) throws IOException {
        super(from(port));
        this.dao = (KVDaoImpl) dao;
        this.logger = Logger.getLogger(KVServiceImpl.class);

        this.myReplicaHost = REPLICA_PATH + port;

        this.replicas = new HashSet<>(replicas);
        this.replicas.remove(this.myReplicaHost);
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
        try {
            checkRequiredRequestParams(request);
        } catch (ReplicaParamsException e) {
            logger.error(e.getMessage(), e);
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        RequestHandleDTO requestHandleDTO = new RequestHandleDTO()
                .setDao(this.dao)
                .setMyReplicaHost(this.myReplicaHost)
                .setReplicasHosts(this.replicas)
                .setRequest(request)
                .setHttpSession(session);

        AbstractHandler handler;

        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET:
                    handler = new GetHandler(requestHandleDTO);
                    break;
                case Request.METHOD_PUT:
                    handler = new PutHandler(requestHandleDTO);
                    break;
                case Request.METHOD_DELETE:
                    handler = new DeleteHandler(requestHandleDTO);
                    break;
                default:
                    session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                    return;
            }
            handler.handle();
        } catch (ReplicaParamsException e) {
            logger.error(e.getMessage(), e);
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }
    }

    private void checkRequiredRequestParams(@NotNull Request request) throws ReplicaParamsException {
        if (!request.getPath().equals(DEFAULT_PATH)) {
            throw new ReplicaParamsException("Invalid path");
        }

        final String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            throw new ReplicaParamsException("Required id param not found");
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

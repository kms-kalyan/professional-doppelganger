package com.doppelganger.llm.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.StatusCodes;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.server.Route;
import com.doppelganger.llm.messages.QueryMessage;
import com.doppelganger.llm.messages.QueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.PathMatchers.segment;
import static akka.http.javadsl.server.Directives.*;

/**
 * HTTP Server Actor that handles web requests
 */
public class HttpServerActor extends AbstractBehavior<HttpServerActor.Command> {
    private static final Logger log = LoggerFactory.getLogger(HttpServerActor.class);
    private final ActorRef<QueryMessage> routingActor;
    private final int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public interface Command {}

    public static class ServerStarted implements Command {
        final ServerBinding binding;
        public ServerStarted(ServerBinding binding) {
            this.binding = binding;
        }
    }

    public static class ServerFailed implements Command {
        final Throwable ex;
        public ServerFailed(Throwable ex) {
            this.ex = ex;
        }
    }

    private HttpServerActor(ActorContext<Command> context, 
                           ActorRef<QueryMessage> routingActor, 
                           int port) {
        super(context);
        this.routingActor = routingActor;
        this.port = port;
        startServer();
    }

    public static Behavior<Command> create(ActorRef<QueryMessage> routingActor, int port) {
        return Behaviors.setup(context -> new HttpServerActor(context, routingActor, port));
    }

    private void startServer() {
        Route route = createRoute();
        CompletionStage<ServerBinding> binding = Http.get(getContext().getSystem())
                .newServerAt("localhost", port)
                .bind(route);

        binding.whenComplete((bindingResult, throwable) -> {
            if (throwable != null) {
                getContext().getSelf().tell(new ServerFailed(throwable));
            } else {
                getContext().getSelf().tell(new ServerStarted(bindingResult));
            }
        });
    }

    private Route createRoute() {
        return concat(
            path(segment("api").slash("query"), () ->
                post(() ->
                    entity(akka.http.javadsl.unmarshalling.Unmarshaller.entityToString(), body -> {
                        try {
                            // Parse JSON request
                            QueryRequest request = objectMapper.readValue(body, QueryRequest.class);
                            String sessionId = UUID.randomUUID().toString();
                            
                            log.info("Received HTTP query: {}", request.query);
                            
                            // Use ask pattern to get response from routing actor
                            CompletionStage<QueryResponse> responseFuture = AskPattern.ask(
                                routingActor,
                                replyTo -> new QueryMessage(
                                    request.query,
                                    sessionId,
                                    replyTo
                                ),
                                Duration.ofSeconds(60),
                                getContext().getSystem().scheduler()
                            );
                            
                            // Return response
                            return onComplete(responseFuture, result -> {
                                if (result.isSuccess()) {
                                    QueryResponse queryResponse = result.get();
                                    try {
                                        String json = objectMapper.writeValueAsString(queryResponse);
                                        return complete(StatusCodes.OK, 
                                            HttpEntities.create(ContentTypes.APPLICATION_JSON, json));
                                    } catch (Exception e) {
                                        return complete(StatusCodes.INTERNAL_SERVER_ERROR, 
                                            "Error serializing response: " + e.getMessage());
                                    }
                                } else {
                                    return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        "Error: " + result.failed().get().getMessage());
                                }
                            });
                        } catch (Exception e) {
                            log.error("Error processing request", e);
                            return complete(StatusCodes.BAD_REQUEST, 
                                "Invalid request: " + e.getMessage());
                        }
                    })
                )
            ),
            get(() ->
                pathEndOrSingleSlash(() ->
                    getFromResource("webapp/index.html")
                )
            ),
            get(() ->
                getFromResourceDirectory("webapp")
            )
        );
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ServerStarted.class, msg -> {
                    log.info("HTTP server started on port {}", port);
                    return Behaviors.same();
                })
                .onMessage(ServerFailed.class, msg -> {
                    log.error("HTTP server failed to start", msg.ex);
                    return Behaviors.stopped();
                })
                .build();
    }

    private static class QueryRequest {
        public String query;
    }
}


package com.doppelganger.llm.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.doppelganger.llm.messages.LLMRequest;
import com.doppelganger.llm.messages.LLMResponse;
import com.doppelganger.llm.messages.LogMessage;
import com.doppelganger.llm.messages.QueryMessage;
import com.doppelganger.llm.messages.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Actor that routes messages to appropriate actors
 * Demonstrates: ask pattern (sends request and waits for response)
 * Demonstrates: tell pattern (sends fire-and-forget messages)
 * Demonstrates: forward pattern (forwards messages to LoggingActor)
 */
public class RoutingActor extends AbstractBehavior<QueryMessage> {
    private static final Logger log = LoggerFactory.getLogger(RoutingActor.class);
    private final ActorRef<LLMRequest> llmActor;
    private final ActorRef<LogMessage> loggingActor;
    
    // Track pending queries by session ID to prevent duplicate processing
    private final Map<String, QueryMessage> pendingQueries = new ConcurrentHashMap<>();

    private RoutingActor(ActorContext<QueryMessage> context,
                        ActorRef<LLMRequest> llmActor,
                        ActorRef<LogMessage> loggingActor) {
        super(context);
        this.llmActor = llmActor;
        this.loggingActor = loggingActor;
        log.info("RoutingActor started on node: {}", context.getSelf().path().address());
    }

    public static Behavior<QueryMessage> create(ActorRef<LLMRequest> llmActor,
                                                ActorRef<LogMessage> loggingActor) {
        return Behaviors.setup(context -> 
            new RoutingActor(context, llmActor, loggingActor));
    }

    @Override
    public Receive<QueryMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryMessage.class, this::handleQueryWithSentinel)
                .build();
    }
    
    private Behavior<QueryMessage> handleQueryWithSentinel(QueryMessage query) {
        // Check for sentinel message from adapter - these should be ignored
        if ("__LLM_RESPONSE__".equals(query.getQuery())) {
            log.debug("Ignoring sentinel message from adapter");
            return this;
        }
        
        // Normal query processing
        return handleQuery(query);
    }

    private Behavior<QueryMessage> handleQuery(QueryMessage query) {
        // Check if this query is already being processed
        if (pendingQueries.containsKey(query.getSessionId())) {
            log.warn("Query already pending for session: {}, ignoring duplicate", query.getSessionId());
            return this;
        }
        
        // Mark as pending
        pendingQueries.put(query.getSessionId(), query);
        
        log.info("RoutingActor received query: {}", query.getQuery());

        // 1. TELL pattern: Send a log message (fire-and-forget)
        LogMessage logMsg = new LogMessage("INFO", 
            "Received query: " + query.getQuery(), 
            query.getSessionId(),
            getContext().getSelf().narrow());
        loggingActor.tell(logMsg);

        // 2. ASK pattern: Request response from LLM actor
        // Store reference for closure
        final QueryMessage queryForResponse = query;
        
        // Create message adapter that converts LLMResponse to QueryMessage
        // BUT we'll use a special marker to prevent reprocessing
        ActorRef<LLMResponse> replyAdapter = getContext().messageAdapter(
            LLMResponse.class, 
            response -> {
                // Handle the response directly - don't let it trigger handleQuery again
                handleLLMResponse(response, queryForResponse);
                // Return a sentinel QueryMessage with special marker that will be ignored
                return new QueryMessage("__LLM_RESPONSE__", response.getSessionId(), 
                    getContext().getSystem().ignoreRef());
            }
        );

        LLMRequest llmRequest = new LLMRequest(
            query.getQuery(), 
            query.getSessionId(), 
            replyAdapter
        );

        // Use tell pattern to send request (LLMActor will respond via replyAdapter)
        llmActor.tell(llmRequest);

        return this;
    }

    private void handleLLMResponse(LLMResponse response, QueryMessage originalQuery) {
        String sessionId = response.getSessionId();
        log.info("RoutingActor received LLM response for session: {}", sessionId);
        
        // Remove from pending queries
        QueryMessage removed = pendingQueries.remove(sessionId);
        if (removed == null) {
            log.warn("Received response for non-pending session: {}", sessionId);
        }

        // 3. FORWARD pattern: Forward log message to LoggingActor with original sender
        LogMessage successLog = new LogMessage("INFO",
            "LLM response generated: " + (response.isSuccess() ? "Success" : "Failed"),
            sessionId,
            getContext().getSelf().narrow());
        
        loggingActor.tell(successLog);

        // Send response back to original requester (HTTP server)
        if (response.isSuccess()) {
            originalQuery.getReplyTo().tell(QueryResponse.success(
                originalQuery.getQuery(),
                response.getResponse(),
                sessionId));
        } else {
            originalQuery.getReplyTo().tell(QueryResponse.failure(
                originalQuery.getQuery(),
                response.getError(),
                sessionId));
        }
    }
}

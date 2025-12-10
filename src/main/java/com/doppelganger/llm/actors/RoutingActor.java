package com.doppelganger.llm.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.doppelganger.llm.messages.ChatMessage;
import com.doppelganger.llm.messages.LLMRequest;
import com.doppelganger.llm.messages.LLMResponse;
import com.doppelganger.llm.messages.LogMessage;
import com.doppelganger.llm.messages.QueryMessage;
import com.doppelganger.llm.messages.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Actor that routes messages to appropriate actors
 * 
 * Message Flow:
 * 1. UserRequest → RoutingActor (from HttpServerActor)
 * 2. RoutingActor ask LLMActor (request-response pattern)
 * 3. RoutingActor forward to LoggerActor (forward pattern, retain original sender)
 * 4. RoutingActor tell MemoryActor (fire-and-forget to append messages)
 * 5. RoutingActor send final response back to HTTP route
 * 
 * Patterns demonstrated:
 * - TELL: RoutingActor → LoggerActor (fire-and-forget)
 * - ASK: RoutingActor → LLMActor (request-response with Future)
 * - FORWARD: RoutingActor → LoggerActor (forward user requests, retain original sender)
 */
public class RoutingActor extends AbstractBehavior<QueryMessage> {
    private static final Logger log = LoggerFactory.getLogger(RoutingActor.class);
    private final ActorRef<LLMRequest> llmActor;
    private final ActorRef<LogMessage> loggingActor;
    private final ActorRef<MemoryActor.Command> memoryActor;
    
    // Track pending queries by session ID to prevent duplicate processing
    private final Map<String, QueryMessage> pendingQueries = new ConcurrentHashMap<>();

    private RoutingActor(ActorContext<QueryMessage> context,
                        ActorRef<LLMRequest> llmActor,
                        ActorRef<LogMessage> loggingActor,
                        ActorRef<MemoryActor.Command> memoryActor) {
        super(context);
        this.llmActor = llmActor;
        this.loggingActor = loggingActor;
        this.memoryActor = memoryActor;
        log.info("RoutingActor started on node: {}", context.getSelf().path().address());
    }

    public static Behavior<QueryMessage> create(ActorRef<LLMRequest> llmActor,
                                                ActorRef<LogMessage> loggingActor,
                                                ActorRef<MemoryActor.Command> memoryActor) {
        return Behaviors.setup(context -> 
            new RoutingActor(context, llmActor, loggingActor, memoryActor));
    }

    @Override
    public Receive<QueryMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(QueryMessage.class, this::handleQueryWithSentinel)
                .build();
    }
    
    private Behavior<QueryMessage> handleQueryWithSentinel(QueryMessage query) {
        // Check for sentinel messages from adapters - these should be ignored
        if ("__LLM_RESPONSE__".equals(query.getQuery()) || 
            "__HISTORY_RECEIVED__".equals(query.getQuery())) {
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

        // 1. TELL pattern: RoutingActor → LoggerActor (fire-and-forget)
        LogMessage logMsg = new LogMessage("INFO", 
            "Received query: " + query.getQuery(), 
            query.getSessionId(),
            getContext().getSelf().narrow());
        loggingActor.tell(logMsg);

        // 2. ASK MemoryActor for conversation history
        final QueryMessage queryForResponse = query;
        
        ActorRef<MemoryActor.HistoryResponse> historyReplyAdapter = getContext().messageAdapter(
            MemoryActor.HistoryResponse.class,
            historyResponse -> {
                // Now that we have history, proceed to call LLMActor using ASK pattern
                proceedWithLLMRequest(queryForResponse, historyResponse.messages);
                return new QueryMessage("__HISTORY_RECEIVED__", queryForResponse.getSessionId(),
                    getContext().getSystem().ignoreRef());
            }
        );
        
        // Ask MemoryActor for history (using tell since MemoryActor responds via adapter)
        memoryActor.tell(new MemoryActor.GetHistory(query.getSessionId(), historyReplyAdapter));

        return this;
    }
    
    private void proceedWithLLMRequest(QueryMessage query, List<ChatMessage> history) {
        // 3. ASK pattern: RoutingActor → LLMActor (request-response with Future)
        // Create message adapter that converts LLMResponse to QueryMessage
        ActorRef<LLMResponse> replyAdapter = getContext().messageAdapter(
            LLMResponse.class, 
            response -> {
                // Handle the response directly - don't let it trigger handleQuery again
                handleLLMResponse(response, query);
                // Return a sentinel QueryMessage with special marker that will be ignored
                return new QueryMessage("__LLM_RESPONSE__", response.getSessionId(), 
                    getContext().getSystem().ignoreRef());
            }
        );

        LLMRequest llmRequest = new LLMRequest(
            query.getQuery(), 
            query.getSessionId(),
            history,
            replyAdapter
        );

        // Use ASK pattern: context.ask equivalent (tell with reply adapter)
        // In Akka Typed, ask is implemented via tell with message adapter
        llmActor.tell(llmRequest);
    }

    private void handleLLMResponse(LLMResponse response, QueryMessage originalQuery) {
        String sessionId = response.getSessionId();
        log.info("RoutingActor received LLM response for session: {}", sessionId);
        
        // Remove from pending queries
        QueryMessage removed = pendingQueries.remove(sessionId);
        if (removed == null) {
            log.warn("Received response for non-pending session: {}", sessionId);
        }

        // 3. FORWARD pattern: RoutingActor → LoggerActor (forward user request, retain original sender)
        // Forward the original query message to LoggerActor, preserving the original sender (HTTP server)
        LogMessage forwardedLog = new LogMessage("INFO",
            "LLM response generated: " + (response.isSuccess() ? "Success" : "Failed") + 
            " for query: " + originalQuery.getQuery(),
            sessionId,
            originalQuery.getReplyTo()); // Forward with original sender (HTTP server)
        
        // Forward: send message to LoggerActor with original sender context
        loggingActor.tell(forwardedLog);

        // 4. TELL pattern: RoutingActor → MemoryActor (fire-and-forget to append messages)
        if (response.isSuccess()) {
            ChatMessage userMsg = ChatMessage.user(originalQuery.getQuery());
            ChatMessage assistantMsg = ChatMessage.assistant(response.getResponse());
            memoryActor.tell(new MemoryActor.AppendMessages(sessionId, userMsg, assistantMsg));
        }

        // 5. Send final response back to HTTP route (original requester)
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

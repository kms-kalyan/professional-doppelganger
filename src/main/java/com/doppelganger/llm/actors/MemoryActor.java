package com.doppelganger.llm.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.doppelganger.llm.messages.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Actor that manages conversation history per session
 * Demonstrates: State management in actors
 */
public class MemoryActor extends AbstractBehavior<MemoryActor.Command> {
    private static final Logger log = LoggerFactory.getLogger(MemoryActor.class);
    
    // Store conversation history per session (max 10 messages = 5 exchanges: user+assistant pairs)
    private final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 10;  // 10 messages = 5 exchanges (user + assistant per exchange)
    
    public interface Command {}
    
    public static class GetHistory implements Command {
        public final String sessionId;
        public final ActorRef<HistoryResponse> replyTo;
        
        public GetHistory(String sessionId, ActorRef<HistoryResponse> replyTo) {
            this.sessionId = sessionId;
            this.replyTo = replyTo;
        }
    }
    
    public static class AppendMessages implements Command {
        public final String sessionId;
        public final ChatMessage userMessage;
        public final ChatMessage assistantMessage;
        
        public AppendMessages(String sessionId, ChatMessage userMessage, ChatMessage assistantMessage) {
            this.sessionId = sessionId;
            this.userMessage = userMessage;
            this.assistantMessage = assistantMessage;
        }
    }
    
    public static class HistoryResponse {
        public final List<ChatMessage> messages;
        
        public HistoryResponse(List<ChatMessage> messages) {
            this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        }
    }
    
    private MemoryActor(ActorContext<Command> context) {
        super(context);
        log.info("MemoryActor started on node: {}", context.getSelf().path().address());
    }
    
    public static Behavior<Command> create() {
        return Behaviors.setup(MemoryActor::new);
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetHistory.class, this::handleGetHistory)
                .onMessage(AppendMessages.class, this::handleAppendMessages)
                .build();
    }
    
    private Behavior<Command> handleGetHistory(GetHistory msg) {
        if (msg.sessionId == null || msg.sessionId.isEmpty()) {
            log.warn("GetHistory called with null or empty sessionId");
            msg.replyTo.tell(new HistoryResponse(new ArrayList<>()));
            return this;
        }
        
        List<ChatMessage> history = sessionHistory.getOrDefault(msg.sessionId, new ArrayList<>());
        String sessionPrefix = msg.sessionId.length() > 8 ? msg.sessionId.substring(0, 8) : msg.sessionId;
        log.info("Retrieved history for session {}: {} messages ({} exchanges)", 
            sessionPrefix, 
            history.size(), 
            history.size() / 2);
        msg.replyTo.tell(new HistoryResponse(history));
        return this;
    }
    
    private Behavior<Command> handleAppendMessages(AppendMessages msg) {
        if (msg.sessionId == null || msg.sessionId.isEmpty()) {
            log.warn("AppendMessages called with null or empty sessionId");
            return this;
        }
        
        List<ChatMessage> history = sessionHistory.computeIfAbsent(msg.sessionId, k -> new ArrayList<>());
        
        // Add new messages
        history.add(msg.userMessage);
        history.add(msg.assistantMessage);
        
        // Keep only last MAX_HISTORY messages (remove oldest if exceeded)
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
        
        String sessionPrefix = msg.sessionId.length() > 8 ? msg.sessionId.substring(0, 8) : msg.sessionId;
        log.info("Appended messages to session {}: {} total messages ({} exchanges)", 
            sessionPrefix, 
            history.size(), 
            history.size() / 2);
        return this;
    }
}


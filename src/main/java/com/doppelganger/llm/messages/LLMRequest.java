package com.doppelganger.llm.messages;

import akka.actor.typed.ActorRef;
import java.util.List;

/**
 * Request message to LLM Actor
 */
public class LLMRequest {
    private final String query;
    private final String sessionId;
    private final List<ChatMessage> history;
    private final ActorRef<LLMResponse> replyTo;

    public LLMRequest(String query, String sessionId, List<ChatMessage> history, ActorRef<LLMResponse> replyTo) {
        this.query = query;
        this.sessionId = sessionId;
        this.history = history;
        this.replyTo = replyTo;
    }

    public String getQuery() {
        return query;
    }

    public String getSessionId() {
        return sessionId;
    }
    
    public List<ChatMessage> getHistory() {
        return history;
    }

    public ActorRef<LLMResponse> getReplyTo() {
        return replyTo;
    }

    @Override
    public String toString() {
        return "LLMRequest{query='" + query + "', sessionId='" + sessionId + "', historySize=" + 
               (history != null ? history.size() : 0) + "}";
    }
}


package com.doppelganger.llm.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request message to LLM Actor
 */
public class LLMRequest {
    private final String query;
    private final String sessionId;
    private final akka.actor.typed.ActorRef<LLMResponse> replyTo;

    @JsonCreator
    public LLMRequest(
            @JsonProperty("query") String query,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("replyTo") akka.actor.typed.ActorRef<LLMResponse> replyTo) {
        this.query = query;
        this.sessionId = sessionId;
        this.replyTo = replyTo;
    }

    public String getQuery() {
        return query;
    }

    public String getSessionId() {
        return sessionId;
    }

    public akka.actor.typed.ActorRef<LLMResponse> getReplyTo() {
        return replyTo;
    }

    @Override
    public String toString() {
        return "LLMRequest{query='" + query + "', sessionId='" + sessionId + "'}";
    }
}


package com.doppelganger.llm.messages;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message representing a user query to be processed by the LLM
 */
public class QueryMessage {
    private final String query;
    private final String sessionId;
    private final ActorRef<QueryResponse> replyTo;

    @JsonCreator
    public QueryMessage(
            @JsonProperty("query") String query,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("replyTo") ActorRef<QueryResponse> replyTo) {
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

    public ActorRef<QueryResponse> getReplyTo() {
        return replyTo;
    }

    @Override
    public String toString() {
        return "QueryMessage{query='" + query + "', sessionId='" + sessionId + "'}";
    }
}


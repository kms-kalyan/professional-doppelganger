package com.doppelganger.llm.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response message for query processing
 */
public class QueryResponse {
    private final String query;
    private final String response;
    private final String sessionId;
    private final boolean success;
    private final String error;

    @JsonCreator
    public QueryResponse(
            @JsonProperty("query") String query,
            @JsonProperty("response") String response,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error) {
        this.query = query;
        this.response = response;
        this.sessionId = sessionId;
        this.success = success;
        this.error = error;
    }

    public static QueryResponse success(String query, String response, String sessionId) {
        return new QueryResponse(query, response, sessionId, true, null);
    }

    public static QueryResponse failure(String query, String error, String sessionId) {
        return new QueryResponse(query, null, sessionId, false, error);
    }

    public String getQuery() {
        return query;
    }

    public String getResponse() {
        return response;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "QueryResponse{success=" + success + ", response='" + response + "'}";
    }
}


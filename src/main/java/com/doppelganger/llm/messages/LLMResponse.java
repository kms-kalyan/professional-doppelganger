package com.doppelganger.llm.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response message from LLM Actor
 */
public class LLMResponse {
    private final String response;
    private final String sessionId;
    private final boolean success;
    private final String error;

    @JsonCreator
    public LLMResponse(
            @JsonProperty("response") String response,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error) {
        this.response = response;
        this.sessionId = sessionId;
        this.success = success;
        this.error = error;
    }

    public static LLMResponse success(String response, String sessionId) {
        return new LLMResponse(response, sessionId, true, null);
    }

    public static LLMResponse failure(String error, String sessionId) {
        return new LLMResponse(null, sessionId, false, error);
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
        return "LLMResponse{success=" + success + ", response='" + response + "'}";
    }
}


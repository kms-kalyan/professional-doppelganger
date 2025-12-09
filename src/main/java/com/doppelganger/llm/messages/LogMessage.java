package com.doppelganger.llm.messages;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Message for logging actor
 */
public class LogMessage {
    private final String level;
    private final String message;
    private final String sessionId;
    private final akka.actor.typed.ActorRef<?> originalSender;

    @JsonCreator
    public LogMessage(
            @JsonProperty("level") String level,
            @JsonProperty("message") String message,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("originalSender") akka.actor.typed.ActorRef<?> originalSender) {
        this.level = level;
        this.message = message;
        this.sessionId = sessionId;
        this.originalSender = originalSender;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public akka.actor.typed.ActorRef<?> getOriginalSender() {
        return originalSender;
    }

    @Override
    public String toString() {
        return "LogMessage{level='" + level + "', message='" + message + "'}";
    }
}


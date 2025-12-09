package com.doppelganger.llm.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.doppelganger.llm.messages.LogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor that handles logging
 * Demonstrates: forward pattern (receives forwarded messages with original sender)
 */
public class LoggingActor extends AbstractBehavior<LogMessage> {
    private static final Logger log = LoggerFactory.getLogger(LoggingActor.class);

    private LoggingActor(ActorContext<LogMessage> context) {
        super(context);
        log.info("LoggingActor started on node: {}", context.getSelf().path().address());
    }

    public static Behavior<LogMessage> create() {
        return Behaviors.setup(LoggingActor::new);
    }

    @Override
    public Receive<LogMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(LogMessage.class, this::handleLogMessage)
                .build();
    }

    private Behavior<LogMessage> handleLogMessage(LogMessage logMessage) {
        // Log the message with the original sender information
        String logEntry = String.format("[%s] Session: %s, Message: %s, OriginalSender: %s",
                logMessage.getLevel(),
                logMessage.getSessionId(),
                logMessage.getMessage(),
                logMessage.getOriginalSender());

        switch (logMessage.getLevel().toUpperCase()) {
            case "INFO":
                log.info(logEntry);
                break;
            case "ERROR":
                log.error(logEntry);
                break;
            case "WARN":
                log.warn(logEntry);
                break;
            default:
                log.debug(logEntry);
        }

        // Note: This demonstrates forward - we received the message with original sender
        // In a real scenario, we might send a confirmation back to the original sender
        return this;
    }
}


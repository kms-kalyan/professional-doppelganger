package com.doppelganger.llm.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.doppelganger.llm.messages.LLMRequest;
import com.doppelganger.llm.messages.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * LLM actor wrapper that implements a primary -> backup failover strategy.
 *
 * Each {@link LLMRequest} is sent to the primary provider first (OpenAI). If the
 * primary returns a failure response (e.g. rate limit / 429, auth error, timeout)
 * or the ask itself times out, the same request is automatically retried against
 * the backup provider (Claude). Whichever response succeeds is returned to the
 * original caller, so {@code RoutingActor} only ever talks to this single actor.
 *
 * Uses the ask pattern + CompletableFuture composition, so there is no shared
 * mutable state and concurrent requests are handled independently.
 */
public class FallbackLLMActor extends AbstractBehavior<LLMRequest> {
    private static final Logger log = LoggerFactory.getLogger(FallbackLLMActor.class);
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(60);

    private final ActorRef<LLMRequest> primary;
    private final ActorRef<LLMRequest> backup; // may be null if no backup is configured

    private FallbackLLMActor(ActorContext<LLMRequest> context,
                             ActorRef<LLMRequest> primary,
                             ActorRef<LLMRequest> backup) {
        super(context);
        this.primary = primary;
        this.backup = backup;
        log.info("FallbackLLMActor started. primary={}, backup={}",
            primary.path().name(), backup != null ? backup.path().name() : "<none>");
    }

    public static Behavior<LLMRequest> create(ActorRef<LLMRequest> primary, ActorRef<LLMRequest> backup) {
        return Behaviors.setup(context -> new FallbackLLMActor(context, primary, backup));
    }

    @Override
    public Receive<LLMRequest> createReceive() {
        return newReceiveBuilder()
                .onMessage(LLMRequest.class, this::handleRequest)
                .build();
    }

    private Behavior<LLMRequest> handleRequest(LLMRequest request) {
        final ActorRef<LLMResponse> originalReplyTo = request.getReplyTo();
        final String sessionId = request.getSessionId();

        askProvider(primary, request)
            // Decide whether the primary succeeded. Returns the good response, or null to trigger fallback.
            .handle((response, error) -> {
                if (error == null && response != null && response.isSuccess()) {
                    return response;
                }
                String reason = error != null
                    ? error.getMessage()
                    : (response != null ? response.getError() : "unknown error");
                if (backup != null) {
                    log.warn("Primary LLM failed for session {} ({}). Falling back to backup.", sessionId, reason);
                } else {
                    log.warn("Primary LLM failed for session {} ({}) and no backup is configured.", sessionId, reason);
                }
                return null;
            })
            // If primary failed and a backup exists, try the backup; otherwise pass through.
            .thenCompose(primaryGood -> {
                if (primaryGood != null) {
                    return CompletableFuture.completedFuture(primaryGood);
                }
                if (backup == null) {
                    return CompletableFuture.completedFuture(LLMResponse.failure(
                        "Primary LLM provider failed and no backup is configured.", sessionId));
                }
                return askProvider(backup, request)
                    .handle((response, error) -> {
                        if (error == null && response != null) {
                            if (response.isSuccess()) {
                                log.info("Backup LLM succeeded for session {}", sessionId);
                            } else {
                                log.error("Backup LLM also failed for session {}: {}", sessionId, response.getError());
                            }
                            return response;
                        }
                        String reason = error != null ? error.getMessage() : "unknown error";
                        log.error("Backup LLM ask failed for session {}: {}", sessionId, reason);
                        return LLMResponse.failure(
                            "Both primary and backup LLM providers failed: " + reason, sessionId);
                    })
                    .toCompletableFuture();
            })
            .whenComplete((finalResponse, error) -> {
                if (finalResponse != null) {
                    originalReplyTo.tell(finalResponse);
                } else {
                    String reason = error != null ? error.getMessage() : "unknown error";
                    log.error("Fallback handling failed for session {}: {}", sessionId, reason);
                    originalReplyTo.tell(LLMResponse.failure("LLM error: " + reason, sessionId));
                }
            });

        return this;
    }

    private CompletionStage<LLMResponse> askProvider(ActorRef<LLMRequest> provider, LLMRequest request) {
        return AskPattern.ask(
            provider,
            (ActorRef<LLMResponse> replyTo) -> new LLMRequest(
                request.getQuery(), request.getSessionId(), request.getHistory(), replyTo),
            ASK_TIMEOUT,
            getContext().getSystem().scheduler());
    }
}

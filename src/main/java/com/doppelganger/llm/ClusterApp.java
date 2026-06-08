package com.doppelganger.llm;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.Cluster;
import com.doppelganger.llm.actors.FallbackLLMActor;
import com.doppelganger.llm.actors.HttpServerActor;
import com.doppelganger.llm.actors.LLMActorClaude;
import com.doppelganger.llm.actors.LLMActorOpenAI;
import com.doppelganger.llm.actors.LoggingActor;
import com.doppelganger.llm.actors.MemoryActor;
import com.doppelganger.llm.actors.RoutingActor;
import com.doppelganger.llm.messages.LogMessage;
import com.doppelganger.llm.messages.LLMRequest;
import com.doppelganger.llm.messages.QueryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Main application that sets up Akka Cluster with multiple nodes
 */
public class ClusterApp {
    private static final Logger log = LoggerFactory.getLogger(ClusterApp.class);

    public static void main(String[] args) {
        // Configuration resolves from positional args first (local dev), then environment
        // variables (deployment platforms inject PORT and secrets as env vars), then defaults.
        int port = parseIntOrDefault(firstNonBlank(arg(args, 0), System.getenv("CLUSTER_PORT")), 2551);
        int httpPort = parseIntOrDefault(firstNonBlank(arg(args, 1), System.getenv("PORT")), 8080);
        String openaiApiKey = firstNonBlank(arg(args, 2), System.getenv("OPENAI_API_KEY"));
        String openaiModel = firstNonBlank(arg(args, 3), System.getenv("OPENAI_MODEL"), "gpt-4o-mini");
        String claudeModel = firstNonBlank(arg(args, 4), System.getenv("CLAUDE_MODEL"), "claude-haiku-4-5");
        // Claude fallback key comes from the environment to keep it out of the process arguments.
        String anthropicApiKey = System.getenv("ANTHROPIC_API_KEY");

        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            System.err.println("Missing OpenAI API key (primary provider).");
            System.err.println("Set the OPENAI_API_KEY environment variable, or pass it as the 3rd argument.");
            System.err.println("Usage: ClusterApp [clusterPort] [httpPort] [openaiApiKey] [openaiModel] [claudeModel]");
            System.err.println("Env vars: PORT, CLUSTER_PORT, OPENAI_API_KEY, ANTHROPIC_API_KEY, OPENAI_MODEL, CLAUDE_MODEL");
            System.exit(1);
        }

        log.info("Starting cluster node on port {} with HTTP server on port {}", port, httpPort);

        // Set system property for cluster port
        System.setProperty("CLUSTER_PORT", String.valueOf(port));

        // Create actor system
        ActorSystem<Void> system = ActorSystem.create(
            createGuardian(port, httpPort, openaiApiKey, openaiModel, anthropicApiKey, claudeModel),
            "ClusterSystem"
        );

        // Get cluster reference
        Cluster cluster = Cluster.get(system);
        log.info("Cluster node started: {}", cluster.selfMember().address());

        // Keep the system running
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down cluster node...");
            system.terminate();
        }));
    }

    private static String arg(String[] args, int index) {
        return index < args.length ? args[index] : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        return value != null ? Integer.parseInt(value.trim()) : defaultValue;
    }

    private static Behavior<Void> createGuardian(int port, int httpPort, String openaiApiKey, String openaiModel,
                                                 String anthropicApiKey, String claudeModel) {
        return Behaviors.setup(context -> {
            // Primary provider: OpenAI. Each LLM actor loads the professional profile JSON internally.
            log.info("Primary LLM provider: OpenAI with model: {}", openaiModel);
            ActorRef<LLMRequest> primaryActor = context.spawn(
                LLMActorOpenAI.create(openaiApiKey, openaiModel),
                "LLMActorOpenAI"
            );

            // Backup provider: Claude (only if an Anthropic API key is available).
            ActorRef<LLMRequest> backupActor = null;
            if (anthropicApiKey != null && !anthropicApiKey.isBlank()) {
                log.info("Fallback LLM provider: Claude with model: {}", claudeModel);
                backupActor = context.spawn(
                    LLMActorClaude.create(anthropicApiKey, claudeModel),
                    "LLMActorClaude"
                );
            } else {
                log.warn("ANTHROPIC_API_KEY not set - Claude fallback is DISABLED. Only OpenAI will be used.");
            }

            // Wrap primary + backup behind a single failover actor; RoutingActor talks only to this.
            ActorRef<LLMRequest> llmActor = context.spawn(
                FallbackLLMActor.create(primaryActor, backupActor),
                "FallbackLLMActor"
            );

            ActorRef<LogMessage> loggingActor = context.spawn(
                LoggingActor.create(),
                "LoggingActor"
            );
            
            ActorRef<MemoryActor.Command> memoryActor = context.spawn(
                MemoryActor.create(),
                "MemoryActor"
            );

            ActorRef<QueryMessage> routingActor = context.spawn(
                RoutingActor.create(llmActor, loggingActor, memoryActor),
                "RoutingActor"
            );

            // Create HTTP server actor
            ActorRef<HttpServerActor.Command> httpServer = context.spawn(
                HttpServerActor.create(routingActor, httpPort),
                "HttpServer"
            );

            log.info("All actors created on node");
            log.info("  - LLMActor: {}", llmActor.path());
            log.info("  - LoggingActor: {}", loggingActor.path());
            log.info("  - RoutingActor: {}", routingActor.path());
            log.info("  - HttpServer: {}", httpServer.path());

            return Behaviors.empty();
        });
    }
}


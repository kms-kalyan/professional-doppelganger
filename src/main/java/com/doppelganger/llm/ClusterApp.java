package com.doppelganger.llm;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.Cluster;
import com.doppelganger.llm.actors.HttpServerActor;
import com.doppelganger.llm.actors.LLMActorGroq;
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
        if (args.length < 3) {
            System.err.println("Usage: ClusterApp <port> <httpPort> <apiKey> [model] [provider]");
            System.err.println("Providers: groq (default, recommended), openai");
            System.err.println("Example (Groq - FREE & FAST): ClusterApp 2551 8080 gsk_... llama-3.1-8b-instant groq");
            System.err.println("Example (OpenAI): ClusterApp 2551 8080 sk-... gpt-3.5-turbo openai");
            System.err.println("Get Groq API key: https://console.groq.com/ (FREE, very fast)");
            System.err.println("Get OpenAI API key: https://platform.openai.com/api-keys");
            System.err.println("Popular Groq models: llama-3.1-8b-instant, mixtral-8x7b-32768, gemma-7b-it");
            System.err.println("Popular OpenAI models: gpt-3.5-turbo, gpt-4, gpt-4-turbo");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        int httpPort = Integer.parseInt(args[1]);
        String apiKey = args[2];
        String model = args.length > 3 ? args[3] : "llama-3.1-8b-instant";
        String provider = args.length > 4 ? args[4].toLowerCase() : "groq";

        log.info("Starting cluster node on port {} with HTTP server on port {}", port, httpPort);

        // Set system property for cluster port
        System.setProperty("CLUSTER_PORT", String.valueOf(port));

        // Create actor system
        ActorSystem<Void> system = ActorSystem.create(
            createGuardian(port, httpPort, apiKey, model, provider),
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

    private static Behavior<Void> createGuardian(int port, int httpPort, String apiKey, String model, String provider) {
        return Behaviors.setup(context -> {
            // Create service actors - choose provider
            // LLMActor will load professional profile JSON internally
            ActorRef<LLMRequest> llmActor;
            if ("groq".equals(provider)) {
                log.info("Using Groq LLM provider with model: {} (FREE & FAST)", model);
                llmActor = context.spawn(
                    LLMActorGroq.create(apiKey, model),
                    "LLMActorGroq"
                );
            } else {
                log.info("Using OpenAI LLM provider with model: {}", model);
                // OpenAI actor also loads profile internally
                llmActor = context.spawn(
                    LLMActorOpenAI.create(apiKey, model),
                    "LLMActorOpenAI"
                );
            }

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


package com.doppelganger.llm.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.doppelganger.llm.ProfessionalDetailsLoader;
import com.doppelganger.llm.messages.ChatMessage;
import com.doppelganger.llm.messages.LLMRequest;
import com.doppelganger.llm.messages.LLMResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Actor that handles communication with Anthropic's Claude API via the official Java SDK.
 * Used as the fallback provider when the primary (OpenAI) fails or is rate-limited.
 * Demonstrates: ask pattern (receives requests and sends responses).
 */
public class LLMActorClaude extends AbstractBehavior<LLMRequest> {
    private static final Logger log = LoggerFactory.getLogger(LLMActorClaude.class);
    private final String model;
    private final String systemPrompt; // Formatted system prompt from professional profile
    private final JsonNode professionalProfile; // Parsed JSON profile
    private final AnthropicClient client;
    private final ObjectMapper objectMapper;

    private LLMActorClaude(ActorContext<LLMRequest> context, String apiKey, String model) {
        super(context);
        this.model = model;
        this.objectMapper = new ObjectMapper();

        // Load and parse professional profile JSON (same source as the other LLM actors)
        this.professionalProfile = loadProfessionalProfile();

        // Build system prompt from parsed profile
        this.systemPrompt = buildSystemPrompt();

        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        log.info("LLMActorClaude started on node: {} with model: {}",
            context.getSelf().path().address(), model);
        if (this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
            log.info("System prompt loaded: {} characters", this.systemPrompt.length());
        }
    }

    public static Behavior<LLMRequest> create(String apiKey, String model) {
        return Behaviors.setup(context -> new LLMActorClaude(context, apiKey, model));
    }

    /**
     * Load professional profile JSON from resources/professional_profile.json
     */
    private JsonNode loadProfessionalProfile() {
        try {
            InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream("professional_profile.json");

            if (resourceStream != null) {
                JsonNode profile = objectMapper.readTree(resourceStream);
                resourceStream.close();
                log.info("Loaded professional profile from resources/professional_profile.json");
                validateProfile(profile);
                logProfile(profile);
                return profile;
            }

            // Try as file path (for development)
            Path filePath = Paths.get("src/main/resources/professional_profile.json");
            if (Files.exists(filePath)) {
                String jsonContent = Files.readString(filePath);
                JsonNode profile = objectMapper.readTree(jsonContent);
                log.info("Loaded professional profile from file: {}", filePath.toAbsolutePath());
                validateProfile(profile);
                logProfile(profile);
                return profile;
            }

            throw new IllegalStateException(
                "Professional profile JSON not found. Expected: resources/professional_profile.json or " +
                "src/main/resources/professional_profile.json");

        } catch (Exception e) {
            log.error("Failed to load professional profile JSON", e);
            throw new IllegalStateException("Cannot start LLMActor without professional profile: " + e.getMessage(), e);
        }
    }

    /**
     * Validate that required fields are present in the profile
     */
    private void validateProfile(JsonNode profile) {
        if (profile == null || profile.isNull()) {
            throw new IllegalStateException("Professional profile JSON is null");
        }
        if (!profile.has("name") || profile.path("name").asText().trim().isEmpty()) {
            throw new IllegalStateException("Professional profile missing required field: 'name'");
        }
        boolean hasContent = profile.has("summary") ||
                           (profile.has("experience") && profile.path("experience").isArray() && profile.path("experience").size() > 0) ||
                           (profile.has("skills") && profile.path("skills").isArray() && profile.path("skills").size() > 0);
        if (!hasContent) {
            throw new IllegalStateException(
                "Professional profile must have at least one of: 'summary', 'experience', or 'skills'");
        }
        log.info("Professional profile validation passed");
    }

    /**
     * Log the parsed profile for debugging
     */
    private void logProfile(JsonNode profile) {
        try {
            String name = profile.has("name") ? profile.path("name").asText() : "Unknown";
            String title = profile.has("title") ? profile.path("title").asText() : "N/A";
            int skillsCount = profile.has("skills") && profile.path("skills").isArray()
                ? profile.path("skills").size() : 0;
            int expCount = profile.has("experience") && profile.path("experience").isArray()
                ? profile.path("experience").size() : 0;

            log.info("=== Professional Profile Loaded (Claude) ===");
            log.info("Name: {}", name);
            log.info("Title: {}", title);
            log.info("Skills: {} items", skillsCount);
            log.info("Experience: {} entries", expCount);
            log.info("============================================");
        } catch (Exception e) {
            log.warn("Could not log profile details: {}", e.getMessage());
        }
    }

    /**
     * Build system prompt from parsed professional profile
     */
    private String buildSystemPrompt() {
        if (professionalProfile == null) {
            return "Keep every response under 150 words, unless user asks to elaborate or explain deeply. Be concise and professional.";
        }
        String basePrompt = ProfessionalDetailsLoader.formatAsSystemPrompt(professionalProfile);
        return basePrompt + "\n\nKeep every response between 100 and 150 words, unless user asks to elaborate or explain deeply. Be concise and professional. Remember: Never mention being an AI or artificial intelligence.";
    }

    @Override
    public Receive<LLMRequest> createReceive() {
        return newReceiveBuilder()
                .onMessage(LLMRequest.class, this::handleLLMRequest)
                .build();
    }

    private Behavior<LLMRequest> handleLLMRequest(LLMRequest request) {
        log.info("LLMActorClaude received request: {} (model: {})", request.getQuery(), model);

        // Process the (blocking) SDK call off the actor thread.
        getContext().getExecutionContext().execute(() -> {
            try {
                List<ChatMessage> history = request.getHistory() != null ? request.getHistory() : new ArrayList<>();

                MessageCreateParams.Builder builder = MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(250L);

                // Claude takes the system prompt as a top-level field (not a message).
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    builder.system(systemPrompt);
                }

                // Replay conversation history as alternating user/assistant turns.
                for (ChatMessage msg : history) {
                    if ("assistant".equalsIgnoreCase(msg.getRole())) {
                        builder.addAssistantMessage(msg.getContent());
                    } else {
                        builder.addUserMessage(msg.getContent());
                    }
                }

                // Current user query.
                builder.addUserMessage(request.getQuery());

                Message message = client.messages().create(builder.build());

                StringBuilder sb = new StringBuilder();
                for (ContentBlock block : message.content()) {
                    block.text().ifPresent(t -> sb.append(t.text()));
                }
                String content = sb.toString().trim();

                if (content.isEmpty()) {
                    log.error("Empty response from Claude API");
                    request.getReplyTo().tell(LLMResponse.failure(
                        "Empty response from Claude API", request.getSessionId()));
                    return;
                }

                log.info("LLMActorClaude generated response for session: {} (history size: {})",
                    request.getSessionId(), history.size());
                request.getReplyTo().tell(LLMResponse.success(content, request.getSessionId()));

            } catch (Exception e) {
                log.error("Error calling Claude API", e);
                request.getReplyTo().tell(LLMResponse.failure(
                    "Error calling Claude API: " + e.getMessage(), request.getSessionId()));
            }
        });

        return this;
    }
}

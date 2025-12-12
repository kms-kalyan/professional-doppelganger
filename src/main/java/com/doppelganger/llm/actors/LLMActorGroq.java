package com.doppelganger.llm.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.doppelganger.llm.ProfessionalDetailsLoader;
import com.doppelganger.llm.messages.ChatMessage;
import com.doppelganger.llm.messages.LLMRequest;
import com.doppelganger.llm.messages.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Actor that handles communication with Groq LLM (Free & Fast alternative)
 * Demonstrates: ask pattern (receives requests and sends responses)
 */
public class LLMActorGroq extends AbstractBehavior<LLMRequest> {
    private static final Logger log = LoggerFactory.getLogger(LLMActorGroq.class);
    private final String apiKey;
    private final String model;
    private final String systemPrompt; // Formatted system prompt from professional profile
    private final JsonNode professionalProfile; // Parsed JSON profile
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private LLMActorGroq(ActorContext<LLMRequest> context, String apiKey, String model) {
        super(context);
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        
        // Load and parse professional profile JSON
        this.professionalProfile = loadProfessionalProfile();
        
        // Build system prompt from parsed profile
        this.systemPrompt = buildSystemPrompt();
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        
        log.info("LLMActorGroq started on node: {} with model: {}", 
            context.getSelf().path().address(), model);
        if (this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
            log.info("System prompt loaded: {} characters", this.systemPrompt.length());
        }
    }

    public static Behavior<LLMRequest> create(String apiKey, String model) {
        return Behaviors.setup(context -> new LLMActorGroq(context, apiKey, model));
    }
    
    /**
     * Load professional profile JSON from resources/professional_profile.json
     */
    private JsonNode loadProfessionalProfile() {
        try {
            // Try loading from resources first
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
            
            // Also try data/professional_details.json for backward compatibility
            resourceStream = getClass().getClassLoader()
                .getResourceAsStream("data/professional_details.json");
            if (resourceStream != null) {
                JsonNode profile = objectMapper.readTree(resourceStream);
                resourceStream.close();
                log.warn("Loaded professional profile from data/professional_details.json (legacy path). Please move to resources/professional_profile.json");
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
        
        // Required fields
        if (!profile.has("name") || profile.path("name").asText().trim().isEmpty()) {
            throw new IllegalStateException("Professional profile missing required field: 'name'");
        }
        
        // At least one of these should be present
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
            
            log.info("=== Professional Profile Loaded ===");
            log.info("Name: {}", name);
            log.info("Title: {}", title);
            log.info("Skills: {} items", skillsCount);
            log.info("Experience: {} entries", expCount);
            log.info("Full JSON: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile));
            log.info("====================================");
        } catch (Exception e) {
            log.warn("Could not log profile details: {}", e.getMessage());
        }
    }
    
    /**
     * Build system prompt from parsed professional profile
     */
    private String buildSystemPrompt() {
        if (professionalProfile == null) {
            return "Answer ONLY what the user asks. Be direct, concise, and avoid any unnecessary information. Keep responses under 100 words unless explicitly asked to elaborate.";
        }
        
        // Use ProfessionalDetailsLoader to format the system prompt
        String basePrompt = ProfessionalDetailsLoader.formatAsSystemPrompt(professionalProfile);
        
        // Append strict response instructions for Groq to reduce noise
        return basePrompt + "\n\nCRITICAL INSTRUCTIONS: Answer ONLY the user's specific question. Be direct and concise. Avoid explanations, elaborations, or additional context unless explicitly requested. Keep responses under 100 words. Do not add introductory phrases, closing statements, or unnecessary details. Stick strictly to what was asked. Never mention being an AI or artificial intelligence.";
    }

    @Override
    public Receive<LLMRequest> createReceive() {
        return newReceiveBuilder()
                .onMessage(LLMRequest.class, this::handleLLMRequest)
                .build();
    }

    private Behavior<LLMRequest> handleLLMRequest(LLMRequest request) {
        log.info("LLMActorGroq received request: {}", request.getQuery());
        
        // Create HTTP request - Groq uses OpenAI-compatible API
        final String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
        log.info("Calling Groq API: {} (model: {})", apiUrl, model);
        
        // Process the request asynchronously
        getContext().getExecutionContext().execute(() -> {
            try {
                // Get conversation history from request
                List<ChatMessage> history = request.getHistory() != null ? request.getHistory() : new ArrayList<>();
                
                // Build messages array with conversation history
                StringBuilder messagesJson = new StringBuilder("[");
                
                // Add system prompt as FIRST element (required format)
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    messagesJson.append(String.format("{\"role\":\"system\",\"content\":\"%s\"},", escapeJson(systemPrompt)));
                }
                
                // Add conversation history
                for (ChatMessage msg : history) {
                    messagesJson.append(String.format("{\"role\":\"%s\",\"content\":\"%s\"},", 
                        msg.getRole(), escapeJson(msg.getContent())));
                }
                
                // Add current user query
                String userQuery = request.getQuery();
                messagesJson.append(String.format("{\"role\":\"user\",\"content\":\"%s\"}", escapeJson(userQuery)));
                messagesJson.append("]");
                
                // Build JSON request body for Groq (OpenAI-compatible format)
                // Reduced max_tokens to 150 to enforce concise responses and reduce noise
                String requestBody = String.format(
                    "{\"model\":\"%s\",\"messages\":%s,\"max_tokens\":150,\"temperature\":0.1}",
                    model,
                    messagesJson.toString()
                );
                log.debug("Request body length: {} characters, history size: {}", requestBody.length(), history.size());
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(60))
                        .build();

                // Send request and get response
                CompletableFuture<HttpResponse<String>> responseFuture = 
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());

                responseFuture.thenAccept(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            JsonNode jsonResponse = objectMapper.readTree(response.body());
                            
                            // Groq returns OpenAI-compatible format
                            String content = jsonResponse
                                    .path("choices")
                                    .get(0)
                                    .path("message")
                                    .path("content")
                                    .asText();

                            if (content == null || content.isEmpty()) {
                                throw new Exception("Empty response from Groq API");
                            }

                            log.info("LLMActorGroq generated response for session: {} (history size: {})", 
                                request.getSessionId(), request.getHistory() != null ? request.getHistory().size() : 0);
                            request.getReplyTo().tell(LLMResponse.success(content, request.getSessionId()));
                            
                        } else if (response.statusCode() == 401) {
                            String errorMsg = "Groq API authentication failed. Please:\n" +
                                "1. Get a free API key from https://console.groq.com/\n" +
                                "2. Update your startup script with your Groq API key";
                            log.error("Groq API authentication failed (401)");
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                            
                        } else if (response.statusCode() == 400) {
                            // Bad request - might be model deprecation or invalid model
                            String errorMsg = "Groq API error (400). ";
                            if (response.body() != null && !response.body().isEmpty()) {
                                try {
                                    JsonNode errorJson = objectMapper.readTree(response.body());
                                    if (errorJson.has("error")) {
                                        JsonNode error = errorJson.path("error");
                                        if (error.has("message")) {
                                            String message = error.path("message").asText();
                                            errorMsg += message;
                                            if (message.contains("decommissioned") || message.contains("deprecated")) {
                                                errorMsg += "\n\nTry using: llama-3.1-8b-instant, mixtral-8x7b-32768, or gemma-7b-it";
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    errorMsg += "Invalid model or request format.";
                                }
                            }
                            log.error("Groq API error (400): {}", errorMsg);
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                            
                        } else if (response.statusCode() == 429) {
                            // Rate limit exceeded - check if there's retry-after header
                            String retryAfter = response.headers().firstValue("retry-after").orElse(null);
                            String errorMsg = "Groq API rate limit exceeded. ";
                            
                            if (retryAfter != null) {
                                try {
                                    int seconds = Integer.parseInt(retryAfter);
                                    errorMsg += String.format("Please wait %d seconds and try again. ", seconds);
                                } catch (NumberFormatException e) {
                                    errorMsg += "Please wait a moment and try again. ";
                                }
                            } else {
                                errorMsg += "Please wait 10-30 seconds and try again. ";
                            }
                            
                            errorMsg += "\n\nNote: Groq free tier has rate limits. " +
                                       "For higher limits, consider upgrading at https://console.groq.com/";
                            
                            log.warn("Groq API rate limit (429) - retry after: {}", retryAfter);
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                            
                        } else {
                            String errorMsg = "Groq API error: HTTP " + response.statusCode();
                            if (response.body() != null && !response.body().isEmpty()) {
                                try {
                                    JsonNode errorJson = objectMapper.readTree(response.body());
                                    if (errorJson.has("error")) {
                                        JsonNode error = errorJson.path("error");
                                        if (error.has("message")) {
                                            errorMsg = error.path("message").asText();
                                        }
                                    }
                                } catch (Exception e) {
                                    // Use default error message
                                }
                            }
                            log.error("Groq API error: {}", errorMsg);
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                        }
                    } catch (Exception e) {
                        log.error("Error parsing Groq response", e);
                        request.getReplyTo().tell(LLMResponse.failure(
                            "Error parsing response: " + e.getMessage(), request.getSessionId()));
                    }
                }).exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    log.error("Error calling Groq API: {} - URL: {}", cause.getMessage(), apiUrl, cause);
                    String errorMsg;
                    if (cause instanceof java.net.ConnectException) {
                        errorMsg = "Cannot connect to Groq API. Please check your internet connection.";
                    } else if (cause instanceof java.net.UnknownHostException) {
                        errorMsg = "Cannot resolve Groq API hostname. Please check your internet connection.";
                    } else {
                        errorMsg = "Error calling Groq API: " + cause.getMessage();
                    }
                    request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                    return null;
                });

            } catch (Exception e) {
                log.error("Error processing LLM request", e);
                request.getReplyTo().tell(LLMResponse.failure(
                    "Error: " + e.getMessage(), request.getSessionId()));
            }
        });

        return this;
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}


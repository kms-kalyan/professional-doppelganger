package com.doppelganger.llm.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
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
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.doppelganger.llm.ProfessionalDetailsLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Actor that handles communication with HuggingFace Inference API (Free alternative)
 * Demonstrates: ask pattern (receives requests and sends responses)
 */
public class LLMActorHuggingFace extends AbstractBehavior<LLMRequest> {
    private static final Logger log = LoggerFactory.getLogger(LLMActorHuggingFace.class);
    private final String apiKey;
    private final String model;
    private final String systemPrompt; // Formatted system prompt from professional profile
    private final JsonNode professionalProfile; // Parsed JSON profile
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private LLMActorHuggingFace(ActorContext<LLMRequest> context, String apiKey, String model) {
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
        
        log.info("LLMActorHuggingFace started on node: {} with model: {}", 
            context.getSelf().path().address(), model);
        if (this.systemPrompt != null && !this.systemPrompt.isEmpty()) {
            log.info("System prompt loaded: {} characters", this.systemPrompt.length());
        }
    }

    public static Behavior<LLMRequest> create(String apiKey, String model) {
        return Behaviors.setup(context -> new LLMActorHuggingFace(context, apiKey, model));
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
            return "Keep every response under 150 words, unless user asks to elaborate or explain deeply. Be concise and professional.";
        }
        
        // Use ProfessionalDetailsLoader to format the system prompt
        String basePrompt = ProfessionalDetailsLoader.formatAsSystemPrompt(professionalProfile);
        
        // Append response length instruction
        return basePrompt + "\n\nKeep every response under 150 words, unless user asks to elaborate or explain deeply. Be concise and professional.";
    }

    @Override
    public Receive<LLMRequest> createReceive() {
        return newReceiveBuilder()
                .onMessage(LLMRequest.class, this::handleLLMRequest)
                .build();
    }

    private Behavior<LLMRequest> handleLLMRequest(LLMRequest request) {
        log.info("LLMActorHuggingFace received request: {}", request.getQuery());
        
        // Create HTTP request - use router.huggingface.co (required, api-inference is deprecated)
        // Router endpoint format: https://router.huggingface.co/models/{model}
        final String apiUrl = "https://router.huggingface.co/models/" + model;
        log.info("Calling HuggingFace API: {} (model: {})", apiUrl, model);
        
        // Process the request asynchronously
        getContext().getExecutionContext().execute(() -> {
            try {
                // Build query with professional details and conversation history
                List<ChatMessage> history = request.getHistory() != null ? request.getHistory() : new ArrayList<>();
                
                // Build context with history
                String queryWithContext = buildQueryWithContext(request.getQuery(), history);
                
                // Build JSON request body for HuggingFace Inference API
                // For text generation models, use "inputs" as a string
                String escapedQuery = escapeJson(queryWithContext);
                String requestBody = String.format(
                    "{\"inputs\":\"%s\",\"parameters\":{\"max_new_tokens\":500,\"temperature\":0.7,\"return_full_text\":false}}",
                    escapedQuery
                );
                log.debug("Request body length: {} characters", requestBody.length());
                
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(60)); // HuggingFace can be slower

                // Add authorization header if API key is provided
                if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("none")) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                    log.debug("Using API key: provided");
                } else {
                    log.debug("No API key provided (may be rate limited)");
                }

                HttpRequest httpRequest = requestBuilder.build();

                // Send request and get response
                CompletableFuture<HttpResponse<String>> responseFuture = 
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());

                responseFuture.thenAccept(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            JsonNode jsonResponse = objectMapper.readTree(response.body());
                            
                            // HuggingFace returns different formats depending on the model
                            String content;
                            if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                                // Some models return array with generated_text
                                JsonNode firstItem = jsonResponse.get(0);
                                if (firstItem.has("generated_text")) {
                                    content = firstItem.path("generated_text").asText();
                                } else if (firstItem.has("summary_text")) {
                                    content = firstItem.path("summary_text").asText();
                                } else {
                                    // Try to get text from any text field
                                    content = firstItem.asText();
                                }
                            } else if (jsonResponse.has("generated_text")) {
                                content = jsonResponse.path("generated_text").asText();
                            } else if (jsonResponse.has("summary_text")) {
                                content = jsonResponse.path("summary_text").asText();
                            } else {
                                // Fallback: get first text value
                                content = jsonResponse.toString();
                            }

                            if (content == null || content.isEmpty()) {
                                throw new Exception("Empty response from HuggingFace API");
                            }

                            log.info("LLMActorHuggingFace generated response for session: {}", request.getSessionId());
                            request.getReplyTo().tell(LLMResponse.success(content, request.getSessionId()));
                            
                        } else if (response.statusCode() == 401) {
                            // Unauthorized - API key issue
                            String errorMsg = "HuggingFace API authentication failed. Please:\n" +
                                "1. Get a free API key from https://huggingface.co/settings/tokens\n" +
                                "2. Update your startup script with: hf_YOUR_API_KEY\n" +
                                "3. The router endpoint requires a valid API key (cannot use 'none')";
                            log.error("HuggingFace API authentication failed (401)");
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                            
                        } else if (response.statusCode() == 404) {
                            // Model not found
                            String errorMsg = String.format(
                                "Model '%s' not found (404). This model may not be available on the HuggingFace Inference API.\n\n" +
                                "Please try a different model. Update your startup script:\n" +
                                "Example: -Dexec.args=\"2551 8080 hf_YOUR_API_KEY microsoft/DialoGPT-small\"\n\n" +
                                "Other models to try:\n" +
                                "- microsoft/DialoGPT-small\n" +
                                "- facebook/blenderbot-400M-distill\n" +
                                "- google/flan-t5-base",
                                model);
                            log.error("HuggingFace model not found (404): {}", model);
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                            
                        } else if (response.statusCode() == 503) {
                            // Model is loading
                            String errorMsg = "HuggingFace model is loading. Please wait a moment and try again.";
                            log.warn("HuggingFace model loading: {}", model);
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                            
                        } else {
                            String errorMsg = "HuggingFace API error: HTTP " + response.statusCode();
                            if (response.body() != null && !response.body().isEmpty()) {
                                try {
                                    JsonNode errorJson = objectMapper.readTree(response.body());
                                    if (errorJson.has("error")) {
                                        errorMsg = errorJson.path("error").asText();
                                    } else if (errorJson.has("message")) {
                                        errorMsg = errorJson.path("message").asText();
                                    }
                                } catch (Exception e) {
                                    // Use default error message
                                }
                            }
                            log.error("HuggingFace API error: {}", errorMsg);
                            request.getReplyTo().tell(LLMResponse.failure(errorMsg, request.getSessionId()));
                        }
                    } catch (Exception e) {
                        log.error("Error parsing HuggingFace response", e);
                        request.getReplyTo().tell(LLMResponse.failure(
                            "Error parsing response: " + e.getMessage(), request.getSessionId()));
                    }
                }).exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    log.error("Error calling HuggingFace API: {} - URL: {}", cause.getMessage(), apiUrl, cause);
                    String errorMsg;
                    if (cause instanceof java.net.ConnectException) {
                        errorMsg = String.format(
                            "Cannot connect to HuggingFace API at %s. Please check:\n" +
                            "1. Your internet connection\n" +
                            "2. Firewall/proxy settings\n" +
                            "3. The model name is correct: %s",
                            apiUrl, model);
                    } else if (cause instanceof java.net.UnknownHostException) {
                        errorMsg = "Cannot resolve HuggingFace API hostname. Please check your internet connection and DNS settings.";
                    } else if (cause instanceof javax.net.ssl.SSLException) {
                        errorMsg = "SSL/TLS error connecting to HuggingFace API. Please check your network settings.";
                    } else if (cause instanceof java.net.SocketTimeoutException) {
                        errorMsg = "Connection timeout. The HuggingFace API may be slow or unavailable. Please try again.";
                    } else {
                        errorMsg = "Error calling HuggingFace API: " + cause.getMessage() + " (URL: " + apiUrl + ")";
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

    private String buildQueryWithContext(String userQuery, java.util.List<ChatMessage> history) {
        // Check if user explicitly asks for elaboration
        String lowerQuery = userQuery.toLowerCase();
        boolean wantsElaboration = lowerQuery.contains("elaborate") || 
                                 lowerQuery.contains("explain in detail") ||
                                 lowerQuery.contains("tell me more") ||
                                 lowerQuery.contains("describe") ||
                                 lowerQuery.contains("detailed");
        
        String lengthInstruction = wantsElaboration 
            ? "Provide a detailed and comprehensive answer." 
            : "Keep your answer brief and concise (2-3 sentences maximum). Only elaborate if the question specifically asks for details.";
        
        // Build conversation context if history exists
        StringBuilder contextBuilder = new StringBuilder();
        if (!history.isEmpty()) {
            contextBuilder.append("Previous conversation:\n");
            for (ChatMessage msg : history) {
                contextBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            contextBuilder.append("\n");
        }
        
        // Format: System prompt + conversation history + instruction + user question
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            return String.format(
                "%s\n\n" +
                "%s" +
                "Additional Instructions: %s\n\n" +
                "Question: %s\n\n" +
                "Answer:",
                systemPrompt,
                contextBuilder.toString(),
                lengthInstruction,
                userQuery
            );
        } else {
            // No system prompt, just use conversation history
            return contextBuilder.toString() + userQuery;
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}


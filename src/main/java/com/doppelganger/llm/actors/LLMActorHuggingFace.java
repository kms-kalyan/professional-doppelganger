package com.doppelganger.llm.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
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
    private final String professionalDetails;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private LLMActorHuggingFace(ActorContext<LLMRequest> context, String apiKey, String model, String professionalDetails) {
        super(context);
        this.apiKey = apiKey;
        this.model = model;
        this.professionalDetails = professionalDetails;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("LLMActorHuggingFace started on node: {} with model: {}", 
            context.getSelf().path().address(), model);
        if (professionalDetails != null && !professionalDetails.isEmpty()) {
            log.info("Professional details loaded: {} characters", professionalDetails.length());
        }
    }

    public static Behavior<LLMRequest> create(String apiKey, String model, String professionalDetails) {
        return Behaviors.setup(context -> new LLMActorHuggingFace(context, apiKey, model, professionalDetails));
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
                // Build query with professional details as context
                String queryWithContext = buildQueryWithContext(request.getQuery());
                
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

    private String buildQueryWithContext(String userQuery) {
        if (professionalDetails != null && !professionalDetails.isEmpty()) {
            // Format: Professional context + instruction to act as doppelganger + user question
            return String.format(
                "You are a professional doppelganger chatbot. Answer questions based on the following professional profile:\n\n" +
                "%s\n\n" +
                "Instructions: Answer questions as if you are this person. Use only the information provided above. " +
                "If asked about something not in the profile, politely say you don't have that information.\n\n" +
                "Question: %s\n\n" +
                "Answer:",
                professionalDetails.length() > 2000 ? professionalDetails.substring(0, 2000) + "..." : professionalDetails,
                userQuery
            );
        } else {
            // No professional details, just answer normally
            return userQuery;
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


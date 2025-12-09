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
 * Actor that handles communication with Groq LLM (Free & Fast alternative)
 * Demonstrates: ask pattern (receives requests and sends responses)
 */
public class LLMActorGroq extends AbstractBehavior<LLMRequest> {
    private static final Logger log = LoggerFactory.getLogger(LLMActorGroq.class);
    private final String apiKey;
    private final String model;
    private final String professionalDetails;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private LLMActorGroq(ActorContext<LLMRequest> context, String apiKey, String model, String professionalDetails) {
        super(context);
        this.apiKey = apiKey;
        this.model = model;
        this.professionalDetails = professionalDetails;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("LLMActorGroq started on node: {} with model: {}", 
            context.getSelf().path().address(), model);
        if (professionalDetails != null && !professionalDetails.isEmpty()) {
            log.info("Professional details loaded: {} characters", professionalDetails.length());
        }
    }

    public static Behavior<LLMRequest> create(String apiKey, String model, String professionalDetails) {
        return Behaviors.setup(context -> new LLMActorGroq(context, apiKey, model, professionalDetails));
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
                // Build query with professional details as context
                String queryWithContext = buildQueryWithContext(request.getQuery());
                
                // Build JSON request body for Groq (OpenAI-compatible format)
                String requestBody = String.format(
                    "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":500,\"temperature\":0.7}",
                    model,
                    escapeJson(queryWithContext)
                );
                log.debug("Request body length: {} characters", requestBody.length());
                
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

                            log.info("LLMActorGroq generated response for session: {}", request.getSessionId());
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


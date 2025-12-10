package com.doppelganger.llm.messages;

import java.io.Serializable;

/**
 * Represents a single message in the conversation history
 */
public class ChatMessage implements Serializable {
    private final String role; // "user" or "assistant"
    private final String content;
    
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
    
    public String getRole() {
        return role;
    }
    
    public String getContent() {
        return content;
    }
    
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
    
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
    
    @Override
    public String toString() {
        return "ChatMessage{role='" + role + "', content='" + content + "'}";
    }
}


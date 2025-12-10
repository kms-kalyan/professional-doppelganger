package com.doppelganger.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loads and formats professional details from JSON file
 */
public class ProfessionalDetailsLoader {
    private static final Logger log = LoggerFactory.getLogger(ProfessionalDetailsLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String loadAndFormat(String jsonPath) {
        try {
            JsonNode json = loadJson(jsonPath);
            if (json == null) {
                return null;
            }
            return formatProfessionalDetails(json);
        } catch (Exception e) {
            log.error("Error loading professional details", e);
            return null;
        }
    }
    
    /**
     * Loads JSON and returns the JsonNode (for system prompt formatting)
     */
    public static JsonNode loadJson(String jsonPath) {
        try {
            // Try multiple locations
            java.util.List<String> pathsToTry = java.util.List.of(
                jsonPath, // User-specified path
                "data/professional_details.json", // Relative to resources
                "src/main/resources/data/professional_details.json" // Development path
            );

            for (String pathStr : pathsToTry) {
                try {
                    // Try as resource first
                    InputStream resourceStream = ProfessionalDetailsLoader.class.getClassLoader()
                        .getResourceAsStream(pathStr);
                    if (resourceStream != null) {
                        JsonNode json = objectMapper.readTree(resourceStream);
                        resourceStream.close();
                        log.info("Loaded professional details from resource: {}", pathStr);
                        return json;
                    }
                    
                    // Try as file path
                    Path path = Paths.get(pathStr);
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        JsonNode json = objectMapper.readTree(Files.readString(path));
                        log.info("Loaded professional details from file: {}", path.toAbsolutePath());
                        return json;
                    }
                } catch (Exception e) {
                    log.debug("Failed to load from {}: {}", pathStr, e.getMessage());
                }
            }

            log.warn("Could not find professional details JSON file. Tried: {}", pathsToTry);
            return null;
            
        } catch (Exception e) {
            log.error("Error loading JSON file", e);
            return null;
        }
    }

    /**
     * Formats professional details as a human-readable system prompt
     */
    public static String formatAsSystemPrompt(JsonNode json) {
        StringBuilder sb = new StringBuilder();
        
        String name = json.has("name") ? json.path("name").asText() : "the professional";
        
        sb.append("You are a professional AI version of ").append(name).append(".\n\n");
        sb.append("Your responses MUST strictly align with the following profile:\n\n");
        
        if (json.has("name")) {
            sb.append("Name: ").append(json.path("name").asText()).append("\n");
        }
        if (json.has("title")) {
            sb.append("Professional Title: ").append(json.path("title").asText()).append("\n");
        }
        if (json.has("email")) {
            sb.append("Email: ").append(json.path("email").asText()).append("\n");
        }
        if (json.has("summary")) {
            sb.append("\nProfessional Summary: ").append(json.path("summary").asText()).append("\n");
        }
        if (json.has("phone")) {
            sb.append("Phone: ").append(json.path("phone").asText()).append("\n");
        }
        if (json.has("address")) {
            sb.append("Address: ").append(json.path("address").asText()).append("\n");
        }
        if (json.has("website")) {
            sb.append("Website: ").append(json.path("website").asText()).append("\n");
        }
        if (json.has("linkedin")) {
            sb.append("LinkedIn: ").append(json.path("linkedin").asText()).append("\n");
        }
        if (json.has("github")) {
            sb.append("GitHub: ").append(json.path("github").asText()).append("\n");
        }
        if (json.has("skills") && json.path("skills").isArray()) {
            sb.append("\nTechnical Skills: ");
            java.util.List<String> skills = new java.util.ArrayList<>();
            json.path("skills").forEach(skill -> skills.add(skill.asText()));
            sb.append(String.join(", ", skills)).append("\n");
        }
        
        if (json.has("experience") && json.path("experience").isArray()) {
            sb.append("\nWork Experience:\n");
            int expNum = 1;
            for (com.fasterxml.jackson.databind.JsonNode exp : json.path("experience")) {
                sb.append(expNum).append(". ");
                if (exp.has("position")) sb.append(exp.path("position").asText());
                if (exp.has("company")) sb.append(" at ").append(exp.path("company").asText());
                if (exp.has("duration")) sb.append(" (").append(exp.path("duration").asText()).append(")");
                sb.append("\n");
                if (exp.has("description")) sb.append("   ").append(exp.path("description").asText()).append("\n");
                expNum++;
            }
        }
        
        if (json.has("education") && json.path("education").isArray()) {
            sb.append("\nEducation:\n");
            for (com.fasterxml.jackson.databind.JsonNode edu : json.path("education")) {
                if (edu.has("degree")) sb.append("- ").append(edu.path("degree").asText());
                if (edu.has("institution")) sb.append(" from ").append(edu.path("institution").asText());
                if (edu.has("year")) {
                    sb.append(" (").append(edu.path("year").asText()).append(")");
                } else if (edu.has("graduation_start_date") || edu.has("graduation_end_date")) {
                    String startDate = edu.has("graduation_start_date") ? edu.path("graduation_start_date").asText() : "";
                    String endDate = edu.has("graduation_end_date") ? edu.path("graduation_end_date").asText() : "";
                    if (!startDate.isEmpty() || !endDate.isEmpty()) {
                        sb.append(" (");
                        if (!startDate.isEmpty()) sb.append(startDate);
                        if (!startDate.isEmpty() && !endDate.isEmpty()) sb.append(" - ");
                        if (!endDate.isEmpty()) sb.append(endDate);
                        sb.append(")");
                    }
                }
                sb.append("\n");
            }
        }
        
        if (json.has("projects") && json.path("projects").isArray()) {
            sb.append("\nNotable Projects:\n");
            int projNum = 1;
            for (com.fasterxml.jackson.databind.JsonNode project : json.path("projects")) {
                sb.append(projNum).append(". ");
                if (project.has("name")) sb.append(project.path("name").asText());
                sb.append("\n");
                if (project.has("description")) sb.append("   ").append(project.path("description").asText()).append("\n");
                if (project.has("technologies") && project.path("technologies").isArray()) {
                    java.util.List<String> techs = new java.util.ArrayList<>();
                    project.path("technologies").forEach(tech -> techs.add(tech.asText()));
                    sb.append("   Technologies: ").append(String.join(", ", techs)).append("\n");
                }
                projNum++;
            }
        }
        
        if (json.has("certifications") && json.path("certifications").isArray() && json.path("certifications").size() > 0) {
            sb.append("\nCertifications: ");
            java.util.List<String> certs = new java.util.ArrayList<>();
            json.path("certifications").forEach(cert -> certs.add(cert.asText()));
            sb.append(String.join(", ", certs)).append("\n");
        }
        
        if (json.has("languages") && json.path("languages").isArray()) {
            sb.append("\nProgramming Languages: ");
            java.util.List<String> langs = new java.util.ArrayList<>();
            json.path("languages").forEach(lang -> langs.add(lang.asText()));
            sb.append(String.join(", ", langs)).append("\n");
        }
        
        if (json.has("leadership") && json.path("leadership").isArray()) {
            sb.append("\nLeadership Roles:\n");
            for (com.fasterxml.jackson.databind.JsonNode lead : json.path("leadership")) {
                if (lead.has("role")) sb.append("- ").append(lead.path("role").asText());
                if (lead.has("organization")) sb.append(" at ").append(lead.path("organization").asText());
                if (lead.has("duration")) sb.append(" (").append(lead.path("duration").asText()).append(")");
                sb.append("\n");
                if (lead.has("description")) sb.append("  ").append(lead.path("description").asText()).append("\n");
            }
        }
        
        sb.append("\n\nSTRICT GROUNDING RULES:\n");
        sb.append("- If the profile shows experience in a skill, technology, or area, you MUST always acknowledge it.\n");
        sb.append("- If asked about a skill, technology, or experience NOT listed in the profile above, respond with: \"This is not listed in my verified experience, but I may have exposure through personal exploration.\"\n");
        sb.append("- NEVER say \"I don't have any professional experience with X\" or similar denials unless the skill/experience is explicitly absent from the profile.\n");
        sb.append("- Always ground your responses in the information provided above. Never contradict this information.\n");
        sb.append("- If something is not in the profile, use the exact phrase: \"This is not listed in my verified experience, but I may have exposure through personal exploration.\"\n");
        sb.append("- Always answer concisely.");
        
        return sb.toString();
    }
    
    private static String formatProfessionalDetails(JsonNode json) {
        // Legacy method for backward compatibility - now uses system prompt format
        return formatAsSystemPrompt(json);
    }
}


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

    private static JsonNode loadJson(String jsonPath) {
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

    private static String formatProfessionalDetails(JsonNode json) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("PROFESSIONAL PROFILE\n");
        sb.append("===================\n\n");
        
        if (json.has("name")) {
            sb.append("Name: ").append(json.path("name").asText()).append("\n");
        }
        if (json.has("title")) {
            sb.append("Title: ").append(json.path("title").asText()).append("\n");
        }
        if (json.has("email")) {
            sb.append("Email: ").append(json.path("email").asText()).append("\n");
        }
        if (json.has("summary")) {
            sb.append("\nSummary:\n").append(json.path("summary").asText()).append("\n");
        }
        
        if (json.has("skills") && json.path("skills").isArray()) {
            sb.append("\nSkills:\n");
            json.path("skills").forEach(skill -> 
                sb.append("  - ").append(skill.asText()).append("\n"));
        }
        
        if (json.has("experience") && json.path("experience").isArray()) {
            sb.append("\nProfessional Experience:\n");
            json.path("experience").forEach(exp -> {
                if (exp.has("company")) sb.append("Company: ").append(exp.path("company").asText()).append("\n");
                if (exp.has("position")) sb.append("Position: ").append(exp.path("position").asText()).append("\n");
                if (exp.has("duration")) sb.append("Duration: ").append(exp.path("duration").asText()).append("\n");
                if (exp.has("description")) sb.append("Description: ").append(exp.path("description").asText()).append("\n");
                sb.append("\n");
            });
        }
        
        if (json.has("education") && json.path("education").isArray()) {
            sb.append("\nEducation:\n");
            json.path("education").forEach(edu -> {
                if (edu.has("institution")) sb.append("Institution: ").append(edu.path("institution").asText()).append("\n");
                if (edu.has("degree")) sb.append("Degree: ").append(edu.path("degree").asText()).append("\n");
                if (edu.has("year")) {
                    sb.append("Year: ").append(edu.path("year").asText()).append("\n");
                } else if (edu.has("graduation_start_date") || edu.has("graduation_end_date")) {
                    String startDate = edu.has("graduation_start_date") ? edu.path("graduation_start_date").asText() : "";
                    String endDate = edu.has("graduation_end_date") ? edu.path("graduation_end_date").asText() : "";
                    if (!startDate.isEmpty() || !endDate.isEmpty()) {
                        sb.append("Duration: ");
                        if (!startDate.isEmpty()) sb.append(startDate);
                        if (!startDate.isEmpty() && !endDate.isEmpty()) sb.append(" - ");
                        if (!endDate.isEmpty()) sb.append(endDate);
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            });
        }
        
        if (json.has("projects") && json.path("projects").isArray()) {
            sb.append("\nProjects:\n");
            json.path("projects").forEach(project -> {
                if (project.has("name")) sb.append("Name: ").append(project.path("name").asText()).append("\n");
                if (project.has("description")) sb.append("Description: ").append(project.path("description").asText()).append("\n");
                if (project.has("technologies")) {
                    sb.append("Technologies: ");
                    project.path("technologies").forEach(tech -> sb.append(tech.asText()).append(", "));
                    sb.append("\n");
                }
                sb.append("\n");
            });
        }
        
        if (json.has("certifications") && json.path("certifications").isArray()) {
            sb.append("\nCertifications:\n");
            json.path("certifications").forEach(cert -> 
                sb.append("  - ").append(cert.asText()).append("\n"));
        }
        
        if (json.has("languages") && json.path("languages").isArray()) {
            sb.append("\nLanguages:\n");
            json.path("languages").forEach(lang -> 
                sb.append("  - ").append(lang.asText()).append("\n"));
        }
        
        if (json.has("interests") && json.path("interests").isArray()) {
            sb.append("\nInterests:\n");
            json.path("interests").forEach(interest -> 
                sb.append("  - ").append(interest.asText()).append("\n"));
        }
        
        if (json.has("leadership") && json.path("leadership").isArray()) {
            sb.append("\nLeadership Roles:\n");
            json.path("leadership").forEach(lead -> {
                if (lead.has("role")) sb.append("Role: ").append(lead.path("role").asText()).append("\n");
                if (lead.has("organization")) sb.append("Organization: ").append(lead.path("organization").asText()).append("\n");
                if (lead.has("duration")) sb.append("Duration: ").append(lead.path("duration").asText()).append("\n");
                if (lead.has("description")) sb.append("Description: ").append(lead.path("description").asText()).append("\n");
                sb.append("\n");
            });
        }
        
        return sb.toString();
    }
}


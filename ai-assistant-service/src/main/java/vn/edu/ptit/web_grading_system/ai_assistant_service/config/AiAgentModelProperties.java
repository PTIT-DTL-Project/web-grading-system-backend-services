package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent/chat LLM settings bound from {@code ai.agent} in {@code application.yaml}.
 * Used by the agent orchestrator/streaming paths.
 */
@ConfigurationProperties(prefix = "ai.agent")
public record AiAgentModelProperties(String provider, String apiKey, String modelName, String baseUrl) {
}

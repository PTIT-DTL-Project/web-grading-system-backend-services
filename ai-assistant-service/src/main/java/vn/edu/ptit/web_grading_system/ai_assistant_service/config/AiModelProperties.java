package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM provider settings, bound from {@code ai.model} in {@code application.yaml}.
 * Blank api-key means fallback mode: the service answers from a local
 * pedagogical template instead of calling the provider — see LlmClient.
 *
 * <p>Review: 2026-10-15, CI/CD alignment — replaces the four scattered
 * {@code @Value} fields in LlmClient with a single source of truth.
 */
@ConfigurationProperties(prefix = "ai.model")
public record AiModelProperties(String provider, String apiKey, String modelName, String baseUrl) {
}

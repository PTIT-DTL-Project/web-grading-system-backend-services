package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Legacy LLM provider settings, bound from {@code ai.model} in {@code application.yaml}.
 * Kept for backward compatibility; prefer {@code ai.agent} or {@code ai.explanation}.
 * Blank api-key means fallback mode: the service answers from a local
 * pedagogical template instead of calling the provider — see LlmClient.
 *
 * <p>Review: 2026-10-15, CI/CD alignment — replaces the four scattered
 * {@code @Value} fields in LlmClient with a single source of truth.
 * <p>Review: 2026-11-01, split by use case — ai.agent and ai.explanation take precedence.
 */
@ConfigurationProperties(prefix = "ai.model")
public record AiModelProperties(String provider, String apiKey, String modelName, String baseUrl) {
}

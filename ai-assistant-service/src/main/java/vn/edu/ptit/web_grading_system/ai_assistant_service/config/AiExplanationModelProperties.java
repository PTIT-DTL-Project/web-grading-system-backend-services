package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Explanation LLM settings bound from {@code ai.explanation} in {@code application.yaml}.
 * Used by {@code AiExplanationService} to generate structured JSON explanations.
 */
@ConfigurationProperties(prefix = "ai.explanation")
public record AiExplanationModelProperties(String provider, String apiKey, String modelName, String baseUrl,
                                          Integer maxCompletionTokens) {
    public int maxCompletionTokensOrDefault(int defaultValue) {
        if (maxCompletionTokens == null || maxCompletionTokens <= 0) {
            return defaultValue;
        }
        return maxCompletionTokens;
    }
}

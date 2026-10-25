package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

/** Marks a provider failure without exposing its response body to the chat UI. */
public class AiProviderException extends RuntimeException {
    public AiProviderException(Throwable cause) {
        super("AI provider request failed", cause);
    }
}

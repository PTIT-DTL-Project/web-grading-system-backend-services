package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Conservative heuristic: ~3-4 characters per token in English, ~2-3 in Vietnamese
        return (int) Math.ceil(text.length() / 3.0);
    }
}

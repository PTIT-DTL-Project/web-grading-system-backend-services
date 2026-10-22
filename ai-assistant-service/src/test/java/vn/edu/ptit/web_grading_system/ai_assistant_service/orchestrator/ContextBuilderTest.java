package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextBuilderTest {

    @Test
    void testBuildPromptContainsRoleAndRules() {
        ContextBuilder builder = new ContextBuilder();

        String prompt = builder.buildSystemPrompt("ROLE_STUDENT", null);
        assertTrue(prompt.contains("WGS"), "Prompt should mention WGS");
        assertTrue(prompt.contains("ROLE_STUDENT"), "Prompt should mention role");
    }

    @Test
    void testBuildPromptWithTask() {
        ContextBuilder builder = new ContextBuilder();
        Task task = Task.builder()
                .stateJson("{\"step\":\"SELECT_ASSIGNMENT\"}")
                .build();

        String prompt = builder.buildSystemPrompt("ROLE_LECTURER", task);
        assertTrue(prompt.contains("ROLE_LECTURER"));
        assertTrue(prompt.contains("SELECT_ASSIGNMENT"));
    }

    @Test
    void testTokenEstimator() {
        TokenEstimator estimator = new TokenEstimator();
        assertEquals(0, estimator.estimateTokens(""));
        assertEquals(0, estimator.estimateTokens(null));
        assertTrue(estimator.estimateTokens("Hello world") > 0);
    }
}

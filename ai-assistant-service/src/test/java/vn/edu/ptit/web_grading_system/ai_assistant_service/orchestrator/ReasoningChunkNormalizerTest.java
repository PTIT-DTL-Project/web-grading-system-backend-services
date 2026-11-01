package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReasoningChunkNormalizerTest {
    @Test
    void acceptsIncrementalAndCumulativeChunksWithoutDuplication() {
        ReasoningChunkNormalizer incremental = new ReasoningChunkNormalizer();
        assertEquals("Let ", incremental.accept("Let "));
        assertEquals("me think", incremental.accept("me think"));

        ReasoningChunkNormalizer cumulative = new ReasoningChunkNormalizer();
        assertEquals("Let ", cumulative.accept("Let "));
        assertEquals("me think", cumulative.accept("Let me think"));
        assertEquals("", cumulative.accept("Let me think"));
    }
}

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
        assertEquals("Let ", cumulative.accept("Let ", true));
        assertEquals("me think", cumulative.accept("Let me think", true));
        assertEquals("", cumulative.accept("Let me think", true));
        ReasoningChunkNormalizer prefixCollision = new ReasoningChunkNormalizer();
        assertEquals("a", prefixCollision.accept("a"));
        assertEquals("and", prefixCollision.accept("and"));
    }
}

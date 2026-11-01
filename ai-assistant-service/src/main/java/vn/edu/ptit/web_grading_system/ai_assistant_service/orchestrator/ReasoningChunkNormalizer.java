package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

/** Converts either cumulative snapshots or ordinary deltas to an append-only stream. */
public final class ReasoningChunkNormalizer {
    private final StringBuilder received = new StringBuilder();

    public String accept(String chunk) {
        return accept(chunk, false);
    }

    public String accept(String chunk, boolean cumulative) {
        if (chunk == null || chunk.isEmpty()) return "";
        if (!cumulative) return chunk;
        String previous = received.toString();
        if (!previous.isEmpty() && chunk.startsWith(previous)) {
            String delta = chunk.substring(previous.length());
            received.append(delta);
            return delta;
        }
        received.append(chunk);
        return chunk;
    }
}

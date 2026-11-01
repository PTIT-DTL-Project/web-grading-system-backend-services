package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record RunTimelineEvent(int version, UUID runId, int sequence, String type,
                               Integer modelTurn, String itemId, Instant timestamp, JsonNode data) {}

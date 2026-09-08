package vn.edu.ptit.web_grading_system.executor_service.event;

import tools.jackson.databind.JsonNode;

/**
 * One handler per action on the shared wgs-events topic. Add a new action by
 * implementing this interface — one class, no topic/consumer changes.
 */
public interface EventHandler {

    WgsEventAction action();

    void handle(JsonNode payload, String traceId);
}
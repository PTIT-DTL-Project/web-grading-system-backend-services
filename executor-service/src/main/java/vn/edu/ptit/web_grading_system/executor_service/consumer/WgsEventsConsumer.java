package vn.edu.ptit.web_grading_system.executor_service.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.event.EventHandler;
import vn.edu.ptit.web_grading_system.executor_service.event.WgsEventAction;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single entry point for the shared wgs-events topic. Parses the envelope,
 * routes by action to a matching {@link EventHandler}. Unknown actions are
 * WARN-logged and acked (no redelivery loop).
 */
@Slf4j
@Component
public class WgsEventsConsumer {

    private final ObjectMapper objectMapper;
    private final Map<WgsEventAction, EventHandler> handlers;

    public WgsEventsConsumer(ObjectMapper objectMapper, List<EventHandler> handlerList) {
        this.objectMapper = objectMapper;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(EventHandler::action, Function.identity()));
    }

    @KafkaListener(topics = "${wgs.events.topic:wgs-events}", groupId = "executor-group")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            String action = root.path("action").asText(null);
            String traceId = root.path("traceId").asText(null);
            JsonNode payload = root.path("payload");

            WgsEventAction parsed = WgsEventAction.fromString(action);
            EventHandler handler = handlers.get(parsed);
            if (handler == null) {
                log.warn("No handler for action={} (acknowledged, skipped). traceId={}", action, traceId);
                return;
            }
            handler.handle(payload, traceId);
        } catch (Exception e) {
            // do not throw — offset still commits, no redelivery loop on malformed messages
            log.error("Failed to process message action=?: {}", e.getMessage(), e);
        }
    }
}
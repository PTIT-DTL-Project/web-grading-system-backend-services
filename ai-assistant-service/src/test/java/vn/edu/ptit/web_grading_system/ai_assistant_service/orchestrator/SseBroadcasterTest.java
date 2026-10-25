package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SseBroadcasterTest {

    @Test
    void testSubscribeAndEmit() {
        SseBroadcaster broadcaster = new SseBroadcaster();
        UUID runId = UUID.randomUUID();

        SseEmitter emitter = broadcaster.subscribe(runId);
        assertNotNull(emitter, "SseEmitter should not be null");

        broadcaster.emit(runId, "assistant.delta", "{\"text\":\"Hello\"}");
        broadcaster.complete(runId);
    }
}

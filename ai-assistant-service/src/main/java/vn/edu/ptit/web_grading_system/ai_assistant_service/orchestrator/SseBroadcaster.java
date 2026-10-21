package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseBroadcaster {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID runId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError(e -> removeEmitter(runId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            removeEmitter(runId, emitter);
        }
        return emitter;
    }

    public void emit(UUID runId, String eventType, Object payload) {
        List<SseEmitter> list = emitters.get(runId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(payload));
            } catch (Exception e) {
                removeEmitter(runId, emitter);
            }
        }
    }

    public void complete(UUID runId) {
        List<SseEmitter> list = emitters.remove(runId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }

    private void removeEmitter(UUID runId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(runId);
            }
        }
    }
}

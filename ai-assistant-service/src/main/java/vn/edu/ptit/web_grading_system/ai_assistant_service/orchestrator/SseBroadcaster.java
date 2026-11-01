package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SseBroadcaster {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "sse-heartbeat"); t.setDaemon(true); return t; });

    public SseEmitter subscribe(UUID runId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
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

    @PostConstruct
    public void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::pingAll, 15, 15, TimeUnit.SECONDS);
    }

    private void pingAll() {
        emitters.forEach((runId, list) -> {
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            dead.forEach(emitter -> removeEmitter(runId, emitter));
        });
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

    public void sendDelta(UUID runId, String delta) {
        emit(runId, "assistant.delta", delta);
    }

    public void sendEvent(UUID runId, String eventType, Object payload) {
        emit(runId, eventType, payload);
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

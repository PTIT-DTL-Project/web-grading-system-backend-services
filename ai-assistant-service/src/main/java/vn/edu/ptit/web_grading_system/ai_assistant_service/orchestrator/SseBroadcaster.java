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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SseBroadcaster {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<UUID, List<ReplaySubscriber>> timelineEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribeReplay(UUID runId, int afterSequence,
                                      BiFunction<UUID, Integer, List<RunTimelineEvent>> replay) {
        SseEmitter emitter = new SseEmitter(180_000L);
        ReplaySubscriber subscriber = new ReplaySubscriber(emitter, afterSequence);
        timelineEmitters.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
        Runnable remove = () -> removeTimelineEmitter(runId, subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try {
            subscriber.replay(replay.apply(runId, afterSequence));
        } catch (Exception error) {
            remove.run();
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private final class ReplaySubscriber {
        private final SseEmitter emitter;
        private final List<RunTimelineEvent> pending = new ArrayList<>();
        private int lastSequence;
        private boolean replaying = true;

        private ReplaySubscriber(SseEmitter emitter, int afterSequence) {
            this.emitter = emitter;
            this.lastSequence = afterSequence;
        }

        synchronized void offer(RunTimelineEvent event) {
            if (replaying) { pending.add(event); return; }
            send(event);
        }

        synchronized void replay(List<RunTimelineEvent> history) {
            ArrayList<RunTimelineEvent> merged = new ArrayList<>(history);
            merged.addAll(pending);
            pending.clear();
            merged.sort(Comparator.comparingInt(RunTimelineEvent::sequence));
            replaying = false;
            for (RunTimelineEvent event : merged) send(event);
        }

        private void send(RunTimelineEvent event) {
            if (event.sequence() <= lastSequence) return;
            try {
                emitter.send(SseEmitter.event().id(Integer.toString(event.sequence()))
                        .name(event.type()).data(JSON.writeValueAsString(event)));
                lastSequence = event.sequence();
                if (event.type().equals("run.completed") || event.type().equals("run.failed")
                        || event.type().equals("run.cancelled")) emitter.complete();
            } catch (Exception error) {
                emitter.completeWithError(error);
            }
        }
    }

    private void removeTimelineEmitter(UUID runId, ReplaySubscriber subscriber) {
        List<ReplaySubscriber> list = timelineEmitters.get(runId);
        if (list != null) {
            list.remove(subscriber);
            if (list.isEmpty()) timelineEmitters.remove(runId, list);
        }
    }

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

    public void sendTimelineEvent(UUID runId, RunTimelineEvent event) {
        List<ReplaySubscriber> list = timelineEmitters.get(runId);
        if (list != null) for (ReplaySubscriber subscriber : list) subscriber.offer(event);
    }

    public void complete(UUID runId) {
        List<ReplaySubscriber> timelineList = timelineEmitters.remove(runId);
        if (timelineList != null) for (ReplaySubscriber subscriber : timelineList) subscriber.emitter.complete();
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

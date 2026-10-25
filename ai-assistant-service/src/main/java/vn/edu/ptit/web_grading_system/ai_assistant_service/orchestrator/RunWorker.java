package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class RunWorker {

    private final AgentOrchestrator agentOrchestrator;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RunWorker(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    public void processRunAsync(UUID runId) {
        executor.submit(() -> {
            try {
                agentOrchestrator.executeRun(runId);
            } catch (Exception e) {
                log.error("Unhandled exception in RunWorker for run {}", runId, e);
            }
        });
    }
}

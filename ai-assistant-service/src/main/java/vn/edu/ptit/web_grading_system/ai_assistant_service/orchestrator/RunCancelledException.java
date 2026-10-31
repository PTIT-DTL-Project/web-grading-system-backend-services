package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

public class RunCancelledException extends RuntimeException {
    public RunCancelledException() {
        super("AI run was cancelled");
    }
}

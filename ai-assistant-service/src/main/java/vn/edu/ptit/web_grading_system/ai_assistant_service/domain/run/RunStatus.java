package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run;

public enum RunStatus {
    QUEUED,
    RUNNING,
    WAITING_USER,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    LIMIT_REACHED
}

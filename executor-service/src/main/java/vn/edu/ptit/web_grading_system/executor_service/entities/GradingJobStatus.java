package vn.edu.ptit.web_grading_system.executor_service.entities;

public enum GradingJobStatus {
    PENDING,
    FETCHING,
    BUILDING,
    RUNNING,
    DONE,
    FAILED
}
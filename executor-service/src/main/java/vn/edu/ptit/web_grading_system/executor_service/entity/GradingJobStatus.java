package vn.edu.ptit.web_grading_system.executor_service.entity;

public enum GradingJobStatus {
    PENDING,
    FETCHING,
    BUILDING,
    RUNNING,
    DONE,
    FAILED
}
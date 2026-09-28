package vn.edu.ptit.web_grading_system.executor_service.service.step;

import vn.edu.ptit.web_grading_system.executor_service.entity.GradingStepResult;

public interface StepExecutor {
    String type();
    GradingStepResult execute(StepContext ctx);
}
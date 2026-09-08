package vn.edu.ptit.web_grading_system.executor_service.service.step;

import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;

public interface StepExecutor {
    String type();
    GradingStepResult execute(HttpStepExecutor.StepContext ctx);
}
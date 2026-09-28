package vn.edu.ptit.web_grading_system.executor_service.service.step;

import java.util.UUID;

import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

/**
 * Parameters carried by every step executor.
 *
 * <p>This is a plain class (not a record) so that the shape can
 * evolve without changing the canonical constructor signature each
 * time a field is added.
 */
public class StepContext {

    private final UUID jobId;
    private final UUID planId;
    private final UUID stepId;
    private final Integer stepOrder;
    private final String stepName;
    private final JsonNode config;
    private final VariableContext variableContext;
    private final Integer timeoutMs;

    public StepContext(UUID jobId,
                       UUID planId,
                       UUID stepId,
                       Integer stepOrder,
                       String stepName,
                       JsonNode config,
                       VariableContext variableContext,
                       Integer timeoutMs) {
        this.jobId = jobId;
        this.planId = planId;
        this.stepId = stepId;
        this.stepOrder = stepOrder;
        this.stepName = stepName;
        this.config = config;
        this.variableContext = variableContext;
        this.timeoutMs = timeoutMs;
    }

    public UUID getJobId() { return jobId; }
    public UUID getPlanId() { return planId; }
    public UUID getStepId() { return stepId; }
    public Integer getStepOrder() { return stepOrder; }
    public String getStepName() { return stepName; }
    public JsonNode getConfig() { return config; }
    public VariableContext getVariableContext() { return variableContext; }
    public Integer getTimeoutMs() { return timeoutMs; }
}

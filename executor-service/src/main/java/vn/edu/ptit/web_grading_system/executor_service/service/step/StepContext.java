package vn.edu.ptit.web_grading_system.executor_service.service.step;

import java.util.UUID;

import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

public record StepContext(
        UUID jobId,
        UUID planId,
        UUID stepId,
        Integer stepOrder,
        String stepName,
        JsonNode config,
        VariableContext variableContext,
        Integer timeoutMs)
{}

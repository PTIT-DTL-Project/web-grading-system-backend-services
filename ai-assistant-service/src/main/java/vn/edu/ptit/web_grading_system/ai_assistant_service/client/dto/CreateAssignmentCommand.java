package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateAssignmentCommand(
        String title,
        UUID classId,
        String gradingStrategy,
        String description,
        String dockerComposeTemplate,
        Integer dockerComposePort,
        Integer startupTimeoutMs,
        Integer executionTimeoutMs,
        Integer maxMemoryMb,
        Double maxCpu
) {
}

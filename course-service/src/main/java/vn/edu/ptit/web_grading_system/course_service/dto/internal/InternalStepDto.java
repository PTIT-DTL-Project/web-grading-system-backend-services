package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import java.util.UUID;
import lombok.Builder;

@Builder
public record InternalStepDto(
        UUID id,
        Integer stepOrder,
        String name,
        String stepType,
        String config,
        String expectedResult,
        Integer weight,
        Integer timeoutMs,
        Boolean required) {
}

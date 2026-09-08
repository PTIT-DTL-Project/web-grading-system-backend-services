package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InternalPlanDto(
        UUID id,
        String name,
        Integer sequenceOrder,
        Integer weight,
        List<InternalStepDto> steps) {
}

package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.course_service.entity.StepType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateStepRequest {
    @NotNull
    private StepType stepType;

    @NotNull
    private JsonNode config;
}

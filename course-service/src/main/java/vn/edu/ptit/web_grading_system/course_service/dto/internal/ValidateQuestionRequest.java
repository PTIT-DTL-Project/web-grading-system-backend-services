package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateStepRequest;

import java.util.List;

/** Request to validate a test-plan payload without persisting it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateQuestionRequest {
    @Valid
    private CreatePlanRequest plan;

    @Valid
    @NotEmpty
    private List<@Valid CreateStepRequest> steps;
}

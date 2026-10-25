package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateQuestionRequest {
    @Valid @NotNull
    private CreatePlanRequest plan;

    @Valid @NotEmpty
    private List<@Valid CreateStepRequest> steps;
}

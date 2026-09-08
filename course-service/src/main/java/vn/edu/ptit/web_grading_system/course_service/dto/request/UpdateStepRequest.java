package vn.edu.ptit.web_grading_system.course_service.dto.request;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;

/**
 * Partial update. If stepType changes, a valid config for the new type MUST be
 * provided in the same request.
 */
@Data
@NoArgsConstructor
public class UpdateStepRequest {

    @Min(0)
    private Integer stepOrder;

    @NotBlank
    private String name;

    private String description;

    private StepType stepType;

    private JsonNode config;

    private JsonNode expectedResult;

    @Min(1)
    private Integer weight;

    @Min(1)
    private Integer timeoutMs;

    private Boolean required;
}
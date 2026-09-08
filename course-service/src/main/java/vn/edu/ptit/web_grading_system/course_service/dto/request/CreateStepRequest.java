package vn.edu.ptit.web_grading_system.course_service.dto.request;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;

@Data
@NoArgsConstructor
public class CreateStepRequest {

    @NotNull
    @Min(0)
    private Integer stepOrder;

    @NotBlank
    private String name;

    /** Lecturer-authored problem-set note shown to students; null/empty -> FE auto-generates text from config. */
    private String description;

    @NotNull
    private StepType stepType;

    /** Raw config object; structure validated per type before persisting. */
    @NotNull
    private JsonNode config;

    /** Optional expected result (JSON), used by DB-type steps. */
    private JsonNode expectedResult;

    @Min(1)
    private Integer weight = 1;

    @Min(1)
    private Integer timeoutMs;

    private Boolean required = true;
}
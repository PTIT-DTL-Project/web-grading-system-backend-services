package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.entities.GradingStrategy;

import java.util.UUID;

/**
 * Note: deliberately NO @Builder here — Lombok @Builder strips field initializers
 * from the no-args constructor, which breaks Jackson's absent-property defaults.
 */
@Data
@NoArgsConstructor
public class CreateAssignmentRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private UUID classId;

    @NotNull
    private GradingStrategy gradingStrategy;

    private String dockerComposeTemplate;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer dockerComposePort = 8080;

    @NotNull
    @Min(1)
    private Integer startupTimeoutMs = 60000;

    @NotNull
    @Min(1)
    private Integer executionTimeoutMs = 300000;

    @NotNull
    @Min(64)
    private Integer maxMemoryMb = 256;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("16.0")
    private Double maxCpu = 0.5;
}
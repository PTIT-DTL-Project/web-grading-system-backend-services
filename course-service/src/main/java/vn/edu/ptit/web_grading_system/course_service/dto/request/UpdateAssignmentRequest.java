package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.entities.GradingStrategy;

import java.util.UUID;

/**
 * class_id is intentionally mutable-checked (not silently moved): if provided and
 * different from the stored class, the service rejects with 400.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAssignmentRequest {

    @NotBlank
    private String title;

    private String description;

    private UUID classId;

    private GradingStrategy gradingStrategy;

    private String dockerComposeTemplate;

    @Min(1)
    @Max(65535)
    private Integer dockerComposePort;

    @Min(1)
    private Integer startupTimeoutMs;

    @Min(1)
    private Integer executionTimeoutMs;

    @Min(64)
    private Integer maxMemoryMb;

    @DecimalMin("0.1")
    @DecimalMax("16.0")
    private Double maxCpu;
}
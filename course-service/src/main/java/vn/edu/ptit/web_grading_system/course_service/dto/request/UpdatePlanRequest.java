package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdatePlanRequest {

    @NotBlank
    private String name;

    private String description;

    @Min(0)
    private Integer sequenceOrder;

    @Min(1)
    private Integer weight;
}
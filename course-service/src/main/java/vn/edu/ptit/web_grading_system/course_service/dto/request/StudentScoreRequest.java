package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.entities.ScoreComponentType;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentScoreRequest {

    @NotNull
    private ScoreComponentType componentType;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("10.00")
    private BigDecimal score;
}
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
public class ScoreComponentRequest {

    @NotNull
    private ScoreComponentType type;

    @NotNull
    @DecimalMin("0.001")
    @DecimalMax("1.000")
    private BigDecimal weight;
}
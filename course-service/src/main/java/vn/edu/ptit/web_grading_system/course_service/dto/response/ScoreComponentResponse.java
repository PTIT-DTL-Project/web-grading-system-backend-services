package vn.edu.ptit.web_grading_system.course_service.dto.response;

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
public class ScoreComponentResponse {
    private ScoreComponentType type;
    private BigDecimal weight;

}
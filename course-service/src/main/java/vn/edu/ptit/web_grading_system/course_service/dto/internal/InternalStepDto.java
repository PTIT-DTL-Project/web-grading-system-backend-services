package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalStepDto {
    private UUID id;
    private Integer stepOrder;
    private String name;
    private String stepType;
    private String config;
    private String expectedResult;
    private Integer weight;
    private Integer timeoutMs;
    private Boolean required;
}

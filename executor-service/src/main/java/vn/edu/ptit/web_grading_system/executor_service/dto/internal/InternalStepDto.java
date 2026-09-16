package vn.edu.ptit.web_grading_system.executor_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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

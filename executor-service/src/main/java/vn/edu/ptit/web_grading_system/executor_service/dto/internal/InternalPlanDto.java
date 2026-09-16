package vn.edu.ptit.web_grading_system.executor_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalPlanDto {
    private UUID id;
    private String name;
    private Integer sequenceOrder;
    private Integer weight;
    private List<InternalStepDto> steps;
}

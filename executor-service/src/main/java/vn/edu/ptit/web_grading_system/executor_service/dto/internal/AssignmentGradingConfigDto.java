package vn.edu.ptit.web_grading_system.executor_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Raw internal contract from course-service. No envelope. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentGradingConfigDto {
    private UUID id;
    private UUID classId;
    private String gradingStrategy;
    private String dockerComposeTemplate;
    private Integer dockerComposePort;
    private Integer startupTimeoutMs;
    private Integer executionTimeoutMs;
    private Integer maxMemoryMb;
    private Double maxCpu;
}

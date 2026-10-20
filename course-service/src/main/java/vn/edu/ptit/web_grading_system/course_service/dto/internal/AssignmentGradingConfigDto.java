package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Raw internal contract for executor-service. No envelope. */
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

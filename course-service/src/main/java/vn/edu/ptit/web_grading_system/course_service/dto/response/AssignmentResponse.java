package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {
    private UUID id;
    private UUID ownerId;
    private UUID classId;
    private String title;
    private String description;
    private String gradingStrategy;
    private String dockerComposeTemplate;
    private Integer dockerComposePort;
    private Integer startupTimeoutMs;
    private Integer executionTimeoutMs;
    private Integer maxMemoryMb;
    private Double maxCpu;
    private Boolean published;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
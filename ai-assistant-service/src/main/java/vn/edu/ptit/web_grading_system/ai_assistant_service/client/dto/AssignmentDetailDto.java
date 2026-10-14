package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentDetailDto {
    private UUID id;
    private String title;
    private String description;
    private Integer executionTimeoutMs;
    private Integer maxMemoryMb;
}

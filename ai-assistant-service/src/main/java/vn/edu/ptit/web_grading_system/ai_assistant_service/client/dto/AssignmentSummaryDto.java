package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssignmentSummaryDto {
    private UUID id;
    private UUID classId;
    private String title;
    private String description;
    private Instant deadline;
    private Boolean published;
}

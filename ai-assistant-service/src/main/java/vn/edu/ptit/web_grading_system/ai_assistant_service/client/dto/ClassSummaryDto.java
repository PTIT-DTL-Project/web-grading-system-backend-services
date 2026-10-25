package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassSummaryDto {
    private UUID id;
    private UUID ownerId;
    private String code;
    private String name;
    private String description;
    private String semester;
    private String status;
    private OffsetDateTime createdAt;
}

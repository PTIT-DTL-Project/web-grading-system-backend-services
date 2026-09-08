package vn.edu.ptit.web_grading_system.course_service.dto.response;

import tools.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResponse {
    private UUID id;
    private Integer stepOrder;
    private String name;
    private String description;
    private String type;
    private JsonNode config;
    private JsonNode expectedResult;
    private Integer weight;
    private Integer timeoutMs;
    private Boolean required;
}
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
public class ClassResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String semester;
    private String status;
    private OffsetDateTime createdAt;

}
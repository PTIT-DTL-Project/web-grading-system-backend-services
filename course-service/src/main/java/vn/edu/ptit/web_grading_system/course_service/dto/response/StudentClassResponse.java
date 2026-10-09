package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Class row for enrolled students. Same shape as {@link ClassResponse} minus
 * {@code ownerId} — the lecturer linkage is not the student's business.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentClassResponse {
    private UUID id;
    private String name;
    private String semester;
    private String status;
    private OffsetDateTime createdAt;
}

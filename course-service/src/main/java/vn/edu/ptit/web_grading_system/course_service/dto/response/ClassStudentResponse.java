package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassStudentResponse {
    private UUID id;
    private String studentCode;
    private String studentName;
    private String email;

}
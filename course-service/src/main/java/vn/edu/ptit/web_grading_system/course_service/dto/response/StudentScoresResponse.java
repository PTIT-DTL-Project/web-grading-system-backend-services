package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentScoresResponse {
    private String studentCode;
    private List<StudentScoreEntryResponse> entries;
    private BigDecimal total;
    private String letterGrade;
    private BigDecimal gpa;
}
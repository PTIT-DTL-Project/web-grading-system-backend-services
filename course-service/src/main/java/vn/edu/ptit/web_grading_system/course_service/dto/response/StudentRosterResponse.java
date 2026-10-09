package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One classmate row visible to enrolled students. Deliberately carries no
 * email or user id — classmates know each other's codes and names; contact
 * details stay lecturer-side. Includes not-yet-linked rows: being imported
 * already makes one a class member.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRosterResponse {
    private String studentCode;
    private String studentName;
}

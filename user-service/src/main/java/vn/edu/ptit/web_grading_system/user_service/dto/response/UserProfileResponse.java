package vn.edu.ptit.web_grading_system.user_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<String> roles;
    private String status;

    // Shared optional attributes
    private String phoneNumber;
    private String gender;
    private String dateOfBirth;
    private String avatarUrl;

    // Student-only attributes
    private String studentCode;
    private String department;
    private String batch;
    private String program;
    private String classCode;

    // Lecturer-only attributes
    private String staffCode;
    private String title;
}

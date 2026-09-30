package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    @Email(message = "Email must be valid")
    private String email;
    private String phoneNumber;
    private String gender;
    private String dateOfBirth;
    private String avatarUrl;
    private String studentCode;
    private String department;
    private String batch;
    private String program;
    private String classCode;
    private String staffCode;
    private String title;
}

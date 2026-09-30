package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Password is required")
    private String password;

    private boolean forcePasswordChange = true;

    /** Role to assign: ROLE_STUDENT, ROLE_LECTURER, or ROLE_ADMIN */
    @NotBlank(message = "Role is required")
    @Pattern(regexp = "ROLE_STUDENT|ROLE_LECTURER|ROLE_ADMIN", message = "Role must be ROLE_STUDENT, ROLE_LECTURER, or ROLE_ADMIN")
    private String role;

    // Shared optional
    private String phoneNumber;
    private String gender;
    private String dateOfBirth;
    private String avatarUrl;

    // Student
    private String studentCode;
    private String department;
    private String batch;
    private String program;
    private String classCode;

    // Lecturer
    private String staffCode;
    private String title;
}

package vn.edu.ptit.web_grading_system.user_service.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String gender;      // MALE / FEMALE / OTHER
    private String dateOfBirth; // ISO: 1999-01-15
    private String avatarUrl;
}

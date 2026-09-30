package vn.edu.ptit.web_grading_system.user_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    /** Present and true only when the user must change password before proceeding. */
    private Boolean requiresPasswordChange;
}

package vn.edu.ptit.web_grading_system.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.user_service.dto.request.*;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.LoginResponse;
import vn.edu.ptit.web_grading_system.user_service.security.SecurityUtils;
import vn.edu.ptit.web_grading_system.user_service.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Change own password. Requires a valid JWT (caller must be authenticated via gateway).
     * Also used for first-login forced password change.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        var principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException(
                        "Not authenticated"));
        authService.changePassword(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}

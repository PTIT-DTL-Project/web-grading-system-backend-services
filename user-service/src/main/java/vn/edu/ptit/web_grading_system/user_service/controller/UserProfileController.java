package vn.edu.ptit.web_grading_system.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.user_service.dto.request.UpdateProfileRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserProfileResponse;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.user_service.security.SecurityUtils;
import vn.edu.ptit.web_grading_system.user_service.service.UserAdminService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserAdminService userAdminService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        var principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Not authenticated"));
        UserProfileResponse profile = userAdminService.getUser(principal.userId().toString());
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        var principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Not authenticated"));
        UserProfileResponse profile = userAdminService.updateUserProfile(
                principal.userId().toString(), request);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}

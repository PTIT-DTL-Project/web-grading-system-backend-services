package vn.edu.ptit.web_grading_system.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.user_service.dto.request.*;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserProfileResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserSummaryResponse;
import vn.edu.ptit.web_grading_system.user_service.service.UserAdminService;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ImportResultResponse;
import vn.edu.ptit.web_grading_system.user_service.service.ImportService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final ImportService importService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<UserSummaryResponse> users = userAdminService.listUsers(role, search, page, size);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserProfileResponse created = userAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(@PathVariable String id) {
        UserProfileResponse user = userAdminService.getUser(id);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserProfileResponse updated = userAdminService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userAdminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    /**
     * Import users from a CSV or Excel file.
     * Form params:
     *   file            — multipart file (.csv or .xlsx)
     *   role            — ROLE_STUDENT or ROLE_LECTURER
     *   passwordMode    — STUDENT_CODE | CUSTOM
     *   defaultPassword — (required when passwordMode=CUSTOM)
     *   forcePasswordChange — true | false (default true)
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ImportResultResponse>> importUsers(
            @RequestParam("file") MultipartFile file,
            @RequestParam("role") String role,
            @RequestParam("passwordMode") String passwordMode,
            @RequestParam(value = "defaultPassword", required = false) String defaultPassword,
            @RequestParam(value = "forcePasswordChange", defaultValue = "true") boolean forcePasswordChange) {
        ImportResultResponse result = importService.importUsers(file, role, passwordMode,
                defaultPassword, forcePasswordChange);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}

package vn.edu.ptit.web_grading_system.user_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.user_service.config.KeycloakProperties;
import vn.edu.ptit.web_grading_system.user_service.dto.request.*;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserProfileResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserSummaryResponse;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.user_service.exception.ConflictException;
import vn.edu.ptit.web_grading_system.user_service.exception.ResourceNotFoundException;

import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final Keycloak keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;

    private org.keycloak.admin.client.resource.RealmResource realm() {
        return keycloakAdminClient.realm(keycloakProperties.realm());
    }

    // ──────────────────────────────────────────────────────────────────
    // Profile operations (own user)
    // ──────────────────────────────────────────────────────────────────

    public UserProfileResponse getUser(String userId) {
        try {
            UserResource userResource = realm().users().get(userId);
            UserRepresentation ur = userResource.toRepresentation();
            List<String> roles = userResource.roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> name.startsWith("ROLE_"))
                    .collect(Collectors.toList());
            return toProfileResponse(ur, roles);
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    public UserProfileResponse updateUserProfile(String userId, UpdateProfileRequest request) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation ur = userResource.toRepresentation();

        if (request.getFirstName() != null) ur.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) ur.setLastName(request.getLastName());

        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes())
                .orElse(new HashMap<>());
        setAttr(attrs, "phone_number", request.getPhoneNumber());
        setAttr(attrs, "gender",       request.getGender());
        setAttr(attrs, "date_of_birth",request.getDateOfBirth());
        setAttr(attrs, "avatar_url",   request.getAvatarUrl());
        ur.setAttributes(attrs);

        userResource.update(ur);
        return getUser(userId);
    }

    // ──────────────────────────────────────────────────────────────────
    // Admin CRUD operations
    // ──────────────────────────────────────────────────────────────────

    public List<UserSummaryResponse> listUsers(String role, String search, int page, int size) {
        List<UserRepresentation> users;
        if (search != null && !search.isBlank()) {
            users = realm().users().search(search, page * size, size);
        } else {
            users = realm().users().list(page * size, size);
        }
        return users.stream()
                .filter(u -> role == null || hasRole(u.getId(), role))
                .map(u -> toSummaryResponse(u, getRolesForUser(u.getId())))
                .collect(Collectors.toList());
    }

    public UserProfileResponse createUser(CreateUserRequest request) {
        // Check username conflict
        List<UserRepresentation> existing = realm().users().searchByUsername(request.getUsername(), true);
        if (!existing.isEmpty()) throw new ConflictException("Username already exists: " + request.getUsername());

        UserRepresentation ur = new UserRepresentation();
        ur.setUsername(request.getUsername());
        ur.setEmail(request.getEmail());
        ur.setFirstName(request.getFirstName());
        ur.setLastName(request.getLastName());
        ur.setEnabled(true);
        ur.setEmailVerified(true);

        // Password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());
        credential.setTemporary(request.isForcePasswordChange());
        ur.setCredentials(List.of(credential));

        if (request.isForcePasswordChange()) {
            ur.setRequiredActions(List.of("UPDATE_PASSWORD"));
        }

        // Attributes
        Map<String, List<String>> attrs = new HashMap<>();
        setAttr(attrs, "status", "ACTIVE");
        setAttr(attrs, "phone_number",  request.getPhoneNumber());
        setAttr(attrs, "gender",        request.getGender());
        setAttr(attrs, "date_of_birth", request.getDateOfBirth());
        setAttr(attrs, "avatar_url",    request.getAvatarUrl());
        // Student
        setAttr(attrs, "student_code",  request.getStudentCode());
        setAttr(attrs, "department",    request.getDepartment());
        setAttr(attrs, "batch",         request.getBatch());
        setAttr(attrs, "program",       request.getProgram());
        setAttr(attrs, "class_code",    request.getClassCode());
        // Lecturer
        setAttr(attrs, "staff_code",    request.getStaffCode());
        setAttr(attrs, "title",         request.getTitle());
        ur.setAttributes(attrs);

        Response response = realm().users().create(ur);
        if (response.getStatus() != 201) {
            throw new BadRequestException("Failed to create user in Keycloak: HTTP " + response.getStatus());
        }

        // Extract created user id from Location header
        String location = response.getHeaderString("Location");
        String newUserId = location.substring(location.lastIndexOf('/') + 1);

        // Assign realm role
        if (request.getRole() != null && !request.getRole().isBlank()) {
            assignRole(newUserId, request.getRole());
        }

        return getUser(newUserId);
    }

    public UserProfileResponse updateUser(String userId, UpdateUserRequest request) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation ur = userResource.toRepresentation();

        if (request.getFirstName() != null) ur.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) ur.setLastName(request.getLastName());
        if (request.getEmail()     != null) ur.setEmail(request.getEmail());

        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes())
                .orElse(new HashMap<>());
        setAttr(attrs, "phone_number",  request.getPhoneNumber());
        setAttr(attrs, "gender",        request.getGender());
        setAttr(attrs, "date_of_birth", request.getDateOfBirth());
        setAttr(attrs, "avatar_url",    request.getAvatarUrl());
        setAttr(attrs, "student_code",  request.getStudentCode());
        setAttr(attrs, "department",    request.getDepartment());
        setAttr(attrs, "batch",         request.getBatch());
        setAttr(attrs, "program",       request.getProgram());
        setAttr(attrs, "class_code",    request.getClassCode());
        setAttr(attrs, "staff_code",    request.getStaffCode());
        setAttr(attrs, "title",         request.getTitle());
        ur.setAttributes(attrs);

        userResource.update(ur);
        return getUser(userId);
    }

    /** Soft-delete: disables user and sets status=INACTIVE attribute. */
    public void deleteUser(String userId) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation ur = userResource.toRepresentation();
        ur.setEnabled(false);
        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes())
                .orElse(new HashMap<>());
        attrs.put("status", List.of("INACTIVE"));
        ur.setAttributes(attrs);
        userResource.update(ur);
    }

    public void resetPassword(String userId, ResetPasswordRequest request) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(request.isForceChange());
        realm().users().get(userId).resetPassword(credential);

        if (request.isForceChange()) {
            UserRepresentation ur = realm().users().get(userId).toRepresentation();
            List<String> actions = new ArrayList<>(
                    Optional.ofNullable(ur.getRequiredActions()).orElse(List.of()));
            if (!actions.contains("UPDATE_PASSWORD")) actions.add("UPDATE_PASSWORD");
            ur.setRequiredActions(actions);
            realm().users().get(userId).update(ur);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private void assignRole(String userId, String roleName) {
        RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
        realm().users().get(userId).roles().realmLevel().add(List.of(role));
    }

    private List<String> getRolesForUser(String userId) {
        try {
            return realm().users().get(userId).roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> name.startsWith("ROLE_"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean hasRole(String userId, String roleName) {
        return getRolesForUser(userId).contains(roleName);
    }

    private String getAttr(Map<String, List<String>> attrs, String key) {
        if (attrs == null) return null;
        List<String> values = attrs.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private void setAttr(Map<String, List<String>> attrs, String key, String value) {
        if (value != null) attrs.put(key, List.of(value));
    }

    public UserProfileResponse toProfileResponse(UserRepresentation ur, List<String> roles) {
        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes()).orElse(Map.of());
        return UserProfileResponse.builder()
                .id(ur.getId())
                .username(ur.getUsername())
                .email(ur.getEmail())
                .firstName(ur.getFirstName())
                .lastName(ur.getLastName())
                .enabled(Boolean.TRUE.equals(ur.isEnabled()))
                .roles(roles)
                .status(getAttr(attrs, "status"))
                .phoneNumber(getAttr(attrs, "phone_number"))
                .gender(getAttr(attrs, "gender"))
                .dateOfBirth(getAttr(attrs, "date_of_birth"))
                .avatarUrl(getAttr(attrs, "avatar_url"))
                .studentCode(getAttr(attrs, "student_code"))
                .department(getAttr(attrs, "department"))
                .batch(getAttr(attrs, "batch"))
                .program(getAttr(attrs, "program"))
                .classCode(getAttr(attrs, "class_code"))
                .staffCode(getAttr(attrs, "staff_code"))
                .title(getAttr(attrs, "title"))
                .build();
    }

    private UserSummaryResponse toSummaryResponse(UserRepresentation ur, List<String> roles) {
        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes()).orElse(Map.of());
        return UserSummaryResponse.builder()
                .id(ur.getId())
                .username(ur.getUsername())
                .email(ur.getEmail())
                .firstName(ur.getFirstName())
                .lastName(ur.getLastName())
                .enabled(Boolean.TRUE.equals(ur.isEnabled()))
                .roles(roles)
                .status(getAttr(attrs, "status"))
                .studentCode(getAttr(attrs, "student_code"))
                .staffCode(getAttr(attrs, "staff_code"))
                .build();
    }
}

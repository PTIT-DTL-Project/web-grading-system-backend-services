package vn.edu.ptit.web_grading_system.submission_service.dto.internal;

import java.util.UUID;

/**
 * Chỉ trả về ownerId của assignment, dùng để submission-service kiểm tra lecturer
 * có quyền xem submission của lớp đó hay không.
 */
public record AssignmentOwnerResponse(UUID ownerId) {
}

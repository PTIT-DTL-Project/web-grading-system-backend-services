package vn.edu.ptit.web_grading_system.submission_service.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.entity.Submission;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks planId into the history payload: the column existed in the table long
 * before the DTO carried it, so whole-vs-single-plan submissions were
 * indistinguishable on the wire (2026-10-10).
 */
class SubmissionMapperTest {

    private final SubmissionMapper mapper = Mappers.getMapper(SubmissionMapper.class);

    @Test
    void toResponse_carriesPlanId() {
        UUID planId = UUID.randomUUID();
        Submission submission = Submission.builder()
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .zipFileName("book-app.zip")
                .planId(planId)
                .build();

        SubmissionResponse response = mapper.toResponse(submission);

        assertEquals(planId, response.getPlanId());
    }

    @Test
    void toResponse_nullPlanIdForWholeAssignment() {
        Submission submission = Submission.builder()
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .zipFileName("book-app.zip")
                .planId(null)
                .build();

        assertNull(mapper.toResponse(submission).getPlanId());
    }
}

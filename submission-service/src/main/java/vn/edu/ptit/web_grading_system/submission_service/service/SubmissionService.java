package vn.edu.ptit.web_grading_system.submission_service.service;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.submission_service.config.SubmissionProperties;
import vn.edu.ptit.web_grading_system.submission_service.event.GradeSubmissionPayload;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEventsProducer;
import vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.submission_service.mapper.SubmissionMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.PresignedUrlResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.entities.Submission;
import vn.edu.ptit.web_grading_system.submission_service.entities.SubmissionStatus;
import vn.edu.ptit.web_grading_system.submission_service.repositories.SubmissionRepository;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
	private final SubmissionMapper submissionMapper;
    private final RustFSService rustfsService;
    private final WgsEventsProducer wgsEventsProducer;
    private final EntityManager entityManager;
    private final SubmissionProperties submissionProperties;

    private long presignedUrlExpiryMinutes() {
        return submissionProperties.presignedUrlExpiryMinutes();
    }

    @Transactional
    public PresignedUrlResponse requestUpload(UUID assignmentId, UUID studentId, String zipFileName, UUID planId) {
        Submission existingLatest = submissionRepository
                .findLatestByAssignmentAndStudent(assignmentId, studentId);
        if (existingLatest != null) {
            existingLatest.setLatest(false);
        }

        UUID submissionId = UUID.randomUUID();
        String objectName = rustfsService.buildObjectName(submissionId);

        Submission submission = Submission.builder()
                .id(submissionId)
                .assignmentId(assignmentId)
                .studentId(studentId)
                .planId(planId)
                .rustfsPath(objectName)
                .zipFileName(zipFileName)
                .status(SubmissionStatus.PENDING)
                .latest(true)
                .build();

        entityManager.persist(submission);

        String uploadUrl = rustfsService.generatePresignedUploadUrl(objectName);

        log.info("Presigned URL generated: submissionId={}, objectName={}", submission.getId(), objectName);

        return PresignedUrlResponse.builder()
                .submissionId(submission.getId())
                .uploadUrl(uploadUrl)
                .objectName(objectName)
                .expiresInMinutes(presignedUrlExpiryMinutes())
                .build();
    }

    @Transactional
    public void handleUploadComplete(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        SubmissionStatus previous = submission.getStatus();
        submission.setStatus(SubmissionStatus.PENDING);
        submissionRepository.save(submission);
        log.info("Upload confirmed by FE: id={}", submissionId);
        publishGradingIfNotStarted(submission, previous);
    }

    @Transactional
    public void handleUploadComplete(String objectName) {
        Submission submission = submissionRepository.findByRustfsPath(objectName)
                .orElse(null);

        if (submission == null) {
            log.warn("Webhook received for unknown object: {}", objectName);
            return;
        }

        SubmissionStatus previous = submission.getStatus();
        submission.setStatus(SubmissionStatus.PENDING);
        submissionRepository.save(submission);
        log.info("Upload confirmed by webhook: id={}, objectName={}", submission.getId(), objectName);
        publishGradingIfNotStarted(submission, previous);
    }

    /**
     * Webhook may fire multiple times for the same object. Skip publishing when
     * the grading pipeline already moved past PENDING (GRADING/DONE/FAILED).
     */
    private void publishGradingIfNotStarted(Submission submission, SubmissionStatus previous) {
        if (previous == SubmissionStatus.GRADING
                || previous == SubmissionStatus.DONE
                || previous == SubmissionStatus.FAILED) {
            log.info("Grading message skipped (already {}): id={}", previous, submission.getId());
            return;
        }
        wgsEventsProducer.publishGradeSubmission(GradeSubmissionPayload.builder()
                .submissionId(submission.getId())
                .assignmentId(submission.getAssignmentId())
                .studentId(submission.getStudentId())
                .planId(submission.getPlanId())
                .rustfsPath(submission.getRustfsPath())
                .build());
    }

    @Async
    public void handleUploadCompleteAsync(String objectName) {
        handleUploadComplete(objectName);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listByStudent(UUID studentId, Pageable pageable) {
        return submissionRepository.findByStudentIdOrderByCreatedAtDesc(studentId, pageable)
                .map(submissionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listByAssignment(UUID assignmentId) {
        return submissionRepository.findByAssignmentIdOrderByCreatedAtDesc(assignmentId)
                .stream()
                .map(submissionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        return submissionMapper.toResponse(submission);
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        submission.setStatus(SubmissionStatus.valueOf(status));
        submissionRepository.save(submission);
        log.info("Submission status updated: id={}, status={}", id, status);
    }

    public String getDownloadUrl(UUID id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));
        return rustfsService.generatePresignedDownloadUrl(submission.getRustfsPath());
    }

    public void streamDownload(UUID id, HttpServletResponse response) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + id));

        String fileName = submission.getZipFileName() != null ? submission.getZipFileName() : id + ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentType("application/zip");

        try (InputStream is = rustfsService.getObject(submission.getRustfsPath())) {
            is.transferTo(response.getOutputStream());
            response.flushBuffer();
            log.info("File streamed: submissionId={}, fileName={}", id, fileName);
        } catch (Exception e) {
            log.error("Failed to stream file for submission: {}", id, e);
            throw new RuntimeException("Failed to stream file", e);
        }
    }
}

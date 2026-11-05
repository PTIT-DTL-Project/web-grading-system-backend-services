package vn.edu.ptit.web_grading_system.submission_service.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.submission_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.submission_service.config.SubmissionProperties;
import vn.edu.ptit.web_grading_system.submission_service.dto.internal.AssignmentOwnerResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipDownloadResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipFileResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipTreeResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.ZipTreeNode;
import vn.edu.ptit.web_grading_system.submission_service.event.GradeSubmissionPayload;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEventsProducer;
import vn.edu.ptit.web_grading_system.submission_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.submission_service.mapper.SubmissionMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.PresignedUrlResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.entity.Submission;
import vn.edu.ptit.web_grading_system.submission_service.entity.SubmissionStatus;
import vn.edu.ptit.web_grading_system.submission_service.repository.SubmissionRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
	private final SubmissionMapper submissionMapper;
    private final RustFSService rustfsService;
    private final CourseServiceClient courseServiceClient;
    private final WgsEventsProducer wgsEventsProducer;
    private final EntityManager entityManager;
    private final SubmissionProperties submissionProperties;

    /** Maximum number of zip entries allowed in the tree (prevents zip bomb). */
    private static final int MAX_ZIP_ENTRIES = 5000;

    /** Maximum total bytes of a zip archive read into memory (prevents OOM on large zips). */
    private static final long MAX_ZIP_BYTES = 10L * 1024 * 1024; // 10 MB

    /** Maximum bytes to read for a single file content (2 MB). */
    private static final int MAX_FILE_CONTENT_BYTES = 2 * 1024 * 1024;

    private long presignedUrlExpiryMinutes() {
        return submissionProperties.presignedUrlExpiryMinutes();
    }

    @Transactional
    public PresignedUrlResponse requestUpload(UUID assignmentId, UUID studentId, String zipFileName, UUID planId) {
        // Demote every previous latest row, not just the first: if a race already left
        // duplicate latest=true rows, this submit collapses them all (self-healing)
        // instead of preserving the inconsistency. Runs in this method's existing
        // @Transactional — flips + new insert commit atomically, same dirty-checking
        // flush as before. Review: 2026-09-28, Pullfrog PR #24.
        submissionRepository.findAllLatestByAssignmentAndStudent(assignmentId, studentId)
                .forEach(previous -> previous.setLatest(false));

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
    public void handleUploadComplete(String objectName) {
        Submission submission = submissionRepository.findByRustfsPath(objectName)
                .orElse(null);

        if (submission == null) {
            log.warn("Webhook received for unknown object: {}", objectName);
            return;
        }

        SubmissionStatus previous = submission.getStatus();
        if (previous == null || previous == SubmissionStatus.PENDING) {
            submission.setStatus(SubmissionStatus.PENDING);
            submissionRepository.save(submission);
            log.info("Upload confirmed by webhook: id={}, objectName={}", submission.getId(), objectName);
        } else {
            // Late/duplicate delivery after grading moved on — never regress the status.
            log.info("Webhook ignored, submission already {}: id={}", previous, submission.getId());
        }
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

    /**
     * Student search (MySubmissionsPage): server-side {@code ?search} match on the zip
     * file name only — {@code Assignment} lives in {@code assignment_db}, not
     * {@code submission_db}, so title search can't be done with a join here.
     * When {@code assignmentId} is present, only submissions for that assignment are returned.
     */
    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listByStudent(UUID studentId, UUID assignmentId, String search, Pageable pageable) {
        if (assignmentId == null) {
            if (search == null || search.isBlank()) {
                return submissionRepository.findByStudentIdOrderByCreatedAtDesc(studentId, pageable)
                        .map(submissionMapper::toResponse);
            }
            return submissionRepository
                    .findByStudentIdAndZipFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
                            studentId, search, pageable)
                    .map(submissionMapper::toResponse);
        }
        if (search == null || search.isBlank()) {
            return submissionRepository.findByStudentIdAndAssignmentIdOrderByCreatedAtDesc(
                    studentId, assignmentId, pageable)
                    .map(submissionMapper::toResponse);
        }
        return submissionRepository
                .findByStudentIdAndAssignmentIdAndZipFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
                        studentId, assignmentId, search, pageable)
                .map(submissionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listByAssignment(UUID assignmentId, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(submissionRepository.findByAssignmentIdOrderByCreatedAtDesc(assignmentId)
                    .stream()
                    .map(submissionMapper::toResponse)
                    .toList());
        }
        return submissionRepository.findByAssignmentIdOrderByCreatedAtDesc(assignmentId, pageable)
                .map(submissionMapper::toResponse);
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

    /**
     * Build the folder/file tree of the submission zip.
     * Only the submission owner (student) may view — lecturer access requires
     * course-service ownership check which is not available in this service.
     */
    @Transactional(readOnly = true)
    public SubmissionZipTreeResponse getSubmissionTree(UUID submissionId, UUID callerId) {
        Submission submission = requireOwnedSubmission(submissionId, callerId);
        byte[] zipBytes = rustfsService.downloadObject(submission.getRustfsPath());
        if (zipBytes.length > MAX_ZIP_BYTES) {
            throw new BadRequestException("Zip too large to preview: " + zipBytes.length + " bytes (max " + MAX_ZIP_BYTES + ")");
        }
        return buildTree(submissionId, zipBytes);
    }

    /**
     * Generate a presigned download URL for the submission zip file.
     * Only the submission owner (student) or the lecturer who owns the assignment's class may download.
     */
    @Transactional(readOnly = true)
    public SubmissionZipDownloadResponse getSubmissionDownloadUrl(UUID submissionId, UUID callerId) {
        Submission submission = requireOwnedSubmission(submissionId, callerId);
        String objectName = submission.getRustfsPath();
        String downloadUrl = rustfsService.generatePresignedDownloadUrl(objectName);
        return SubmissionZipDownloadResponse.builder()
                .submissionId(submissionId)
                .downloadUrl(downloadUrl)
                .expiresInMinutes(presignedUrlExpiryMinutes())
                .build();
    }

    /**
     * Read the content of a single file entry inside the submission zip.
     * Returns text content for text files, or isBinary=true for binary files.
     */
    @Transactional(readOnly = true)
    public SubmissionZipFileResponse getZipFileContent(UUID submissionId, String path, UUID callerId) {
        Submission submission = requireOwnedSubmission(submissionId, callerId);
        String sanitizedPath = sanitizeZipPath(path);
        byte[] zipBytes = rustfsService.downloadObject(submission.getRustfsPath());
        if (zipBytes.length > MAX_ZIP_BYTES) {
            throw new BadRequestException("Zip too large to preview: " + zipBytes.length + " bytes (max " + MAX_ZIP_BYTES + ")");
        }

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            boolean found = false;
            long entrySize = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(sanitizedPath)) {
                    found = true;
                    entrySize = entry.getSize();
                    break;
                }
                zis.closeEntry();
            }

            if (!found) {
                throw new ResourceNotFoundException("File not found in zip: " + path);
            }

            String mime = detectMime(sanitizedPath);
            boolean isBinary = !isTextMime(mime);

            if (isBinary) {
                return SubmissionZipFileResponse.builder()
                        .submissionId(submissionId)
                        .path(sanitizedPath)
                        .size(entrySize)
                        .mime(mime)
                        .isBinary(true)
                        .content(null)
                        .truncated(false)
                        .message("File nhị phân, không thể xem trước trong editor")
                        .build();
            }

            long size = entrySize;
            boolean truncated;
            int contentLen;
            byte[] contentBytes;
            if (size < 0) {
                // Size unknown (streaming zip entry): read the whole entry into memory, capped at
                // MAX_FILE_CONTENT_BYTES so a large entry cannot OOM the service.
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int read;
                while ((read = zis.read(buf)) > 0) {
                    bos.write(buf, 0, read);
                }
                contentBytes = bos.toByteArray();
                contentLen = contentBytes.length;
                size = contentLen;
                truncated = contentLen > MAX_FILE_CONTENT_BYTES;
                if (truncated) {
                    byte[] truncatedBytes = new byte[MAX_FILE_CONTENT_BYTES];
                    System.arraycopy(contentBytes, 0, truncatedBytes, 0, MAX_FILE_CONTENT_BYTES);
                    contentBytes = truncatedBytes;
                    contentLen = MAX_FILE_CONTENT_BYTES;
                }
            } else {
                truncated = size > MAX_FILE_CONTENT_BYTES;
                int readBytes = truncated ? MAX_FILE_CONTENT_BYTES : (int) size;
                contentBytes = new byte[readBytes];
                contentLen = 0;
                while (contentLen < readBytes) {
                    int read = zis.read(contentBytes, contentLen, readBytes - contentLen);
                    if (read < 0) {
                        break;
                    }
                    contentLen += read;
                }
            }

            String content = new String(contentBytes, 0, contentLen, java.nio.charset.StandardCharsets.UTF_8);

            return SubmissionZipFileResponse.builder()
                    .submissionId(submissionId)
                    .path(sanitizedPath)
                    .size(entrySize)
                    .mime(mime)
                    .isBinary(false)
                    .content(content)
                    .truncated(truncated)
                    .message(truncated ? "File quá lớn, chỉ hiển thị phần đầu (" + MAX_FILE_CONTENT_BYTES / 1024 + "KB)" : null)
                    .build();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read zip entry {} from submission {}", sanitizedPath, submissionId, e);
            throw new RuntimeException("Failed to read file from submission zip", e);
        }
    }

    private Submission requireOwnedSubmission(UUID submissionId, UUID callerId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        if (submission.getStudentId().equals(callerId)) {
            return submission;
        }
        // Lecturer access: verify callerId is the owner (giảng viên) of the assignment's class.
        UUID ownerId = courseServiceClient.getAssignmentOwner(submission.getAssignmentId()).ownerId();
        if (!ownerId.equals(callerId)) {
            throw new ResourceNotFoundException("Submission not found: " + submissionId);
        }
        return submission;
    }

    private SubmissionZipTreeResponse buildTree(UUID submissionId, byte[] zipBytes) {
        Map<String, ZipTreeNode> nodeByPath = new LinkedHashMap<>();
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Skip system files
                if (name.startsWith("__MACOSX/") || name.equals(".DS_Store")) {
                    zis.closeEntry();
                    continue;
                }

                // Reject zip slip: absolute paths or path traversal
                if (name.startsWith("/") || name.contains("..")) {
                    throw new BadRequestException("Zip contains unsafe path: " + name);
                }

                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException("Zip contains too many entries (max " + MAX_ZIP_ENTRIES + ")");
                }

                String mime = detectMime(name);
                boolean isDirectory = entry.isDirectory();
                String nodePath = isDirectory ? stripTrailingSlash(name) : name;

                ZipTreeNode node = ZipTreeNode.builder()
                        .name(entryName(name))
                        .type(isDirectory ? "folder" : "file")
                        .path(nodePath)
                        .size(isDirectory ? null : entry.getSize())
                        .mime(isDirectory ? null : mime)
                        .children(isDirectory ? new ArrayList<>() : null)
                        .build();

                nodeByPath.put(nodePath, node);
                zis.closeEntry();
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read zip for submission {}", submissionId, e);
            throw new RuntimeException("Failed to read submission zip", e);
        }

        // Build parent-child relationships
        // Iterate on a copy of values: implicit parent folders are put back into
        // nodeByPath below (line ~412), which would cause ConcurrentModificationException
        // if we iterated the live map. Review: 2026-11-05, zip viewer unit tests.
        ZipTreeNode root = ZipTreeNode.builder()
                .name("root")
                .type("folder")
                .path("")
                .size(null)
                .mime(null)
                .children(new ArrayList<>())
                .build();
        List<ZipTreeNode> nodes = new ArrayList<>(nodeByPath.values());
        for (ZipTreeNode node : nodes) {
            if (node.getPath().isEmpty()) {
                continue; // skip virtual root
            }
            String parentPath = parentPathOf(node.getPath());
            if (parentPath.isEmpty()) {
                root.getChildren().add(node);
            } else {
                ZipTreeNode parent = nodeByPath.get(parentPath);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // Parent folder not explicitly in entries (e.g. zip has "a/b.txt" but no "a/")
                    // Create an implicit folder node
                    ZipTreeNode implicitParent = ZipTreeNode.builder()
                            .name(entryName(parentPath))
                            .type("folder")
                            .path(parentPath)
                            .size(null)
                            .mime(null)
                            .children(new ArrayList<>(List.of(node)))
                            .build();
                    nodeByPath.put(parentPath, implicitParent);
                    root.getChildren().add(implicitParent);
                }
            }
        }

        return SubmissionZipTreeResponse.builder()
                .submissionId(submissionId)
                .root(root)
                .build();
    }

    private String sanitizeZipPath(String path) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("Path is required");
        }
        String p = path.trim();
        // Reject path traversal and absolute paths
        if (p.startsWith("/") || p.contains("..")) {
            throw new BadRequestException("Invalid path: " + path);
        }
        return p;
    }

    private String entryName(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }

    private String stripTrailingSlash(String path) {
        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String parentPathOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return path.substring(0, lastSlash);
    }

    private String detectMime(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "text/x-java";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".js")) return "text/javascript";
        if (lower.endsWith(".ts")) return "text/typescript";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".kt")) return "text/x-kotlin";
        if (lower.endsWith(".go")) return "text/x-go";
        if (lower.endsWith(".rs")) return "text/x-rust";
        if (lower.endsWith(".c")) return "text/x-c";
        if (lower.endsWith(".cpp")) return "text/x-c";
        if (lower.endsWith(".h")) return "text/x-c";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "text/yaml";
        if (lower.endsWith(".toml")) return "text/toml";
        if (lower.endsWith(".ini")) return "text/ini";
        if (lower.endsWith(".cfg")) return "text/ini";
        if (lower.endsWith(".properties")) return "text/plain";
        if (lower.endsWith(".sql")) return "text/x-sql";
        if (lower.endsWith(".sh")) return "text/x-shellscript";
        if (lower.endsWith(".bat")) return "text/x-batch";
        if (lower.endsWith(".log")) return "text/plain";
        return "application/octet-stream";
    }

    private boolean isTextMime(String mime) {
        return mime != null && mime.startsWith("text/");
    }
}

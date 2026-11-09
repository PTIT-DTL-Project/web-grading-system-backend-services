package vn.edu.ptit.web_grading_system.submission_service.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.submission_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.submission_service.config.SubmissionProperties;
import vn.edu.ptit.web_grading_system.submission_service.dto.internal.AssignmentOwnerResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipDownloadResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipFileResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipTreeResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.ZipTreeNode;
import vn.edu.ptit.web_grading_system.submission_service.entity.Submission;
import vn.edu.ptit.web_grading_system.submission_service.entity.SubmissionStatus;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEventsProducer;
import vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.submission_service.mapper.SubmissionMapper;
import vn.edu.ptit.web_grading_system.submission_service.repository.SubmissionRepository;
import vn.edu.ptit.web_grading_system.submission_service.service.RustFSService;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for zip viewer service methods:
 * {@link SubmissionService#getSubmissionTree(UUID, UUID)},
 * {@link SubmissionService#getSubmissionDownloadUrl(UUID, UUID)},
 * {@link SubmissionService#getZipFileContent(UUID, String, UUID)}.
 */
class SubmissionServiceZipViewerTest {

    private static final UUID SUBMISSION_ID = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID STUDENT_ID = UUID.fromString("3e93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID LECTURER_ID = UUID.fromString("4e93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID OTHER_ID = UUID.fromString("5e93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("6e93941a-4221-458b-a03d-43bd6315d02e");

    private static Tracer noopTracer() {
        Tracer tracer = Mockito.mock(Tracer.class);
        Span span = Mockito.mock(Span.class);
        Tracer.SpanInScope scope = Mockito.mock(Tracer.SpanInScope.class);
        Mockito.when(tracer.nextSpan()).thenReturn(span);
        Mockito.when(span.name(anyString())).thenReturn(span);
        Mockito.when(span.start()).thenReturn(span);
        Mockito.when(span.error(any(Throwable.class))).thenReturn(span);
        Mockito.when(tracer.withSpan(any(Span.class))).thenReturn(scope);
        return tracer;
    }

    private SubmissionService newService(SubmissionRepository repo, CourseServiceClient client,
                                         RustFSService rustfs, SubmissionProperties props) {
        return new SubmissionService(
                repo,
                Mockito.mock(SubmissionMapper.class),
                rustfs,
                client,
                Mockito.mock(WgsEventsProducer.class),
                Mockito.mock(EntityManager.class),
                props,
                noopTracer());
    }

    private Submission submission(String rustfsPath, UUID studentId) {
        return Submission.builder()
                .id(SUBMISSION_ID)
                .assignmentId(ASSIGNMENT_ID)
                .studentId(studentId)
                .rustfsPath(rustfsPath)
                .zipFileName("app.zip")
                .status(SubmissionStatus.DONE)
                .latest(true)
                .build();
    }

    /**
     * Build a small in-memory zip containing:
     *   src/Main.java      (text)
     *   images/logo.png     (binary)
     *   folder/             (folder entry)
     *   pom.xml             (xml text)
     *   Dockerfile          (extensionless text)
     *   mvnw                (extensionless shell script)
     */
    private byte[] buildZipBytes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("src/Main.java"));
            zos.write("public class Main {}".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("images/logo.png"));
            zos.write(new byte[]{0, 1, 2, 3});
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("folder/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("pom.xml"));
            zos.write("<project><modelVersion>4.0.0</modelVersion></project>".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("Dockerfile"));
            zos.write("FROM eclipse-temurin:21-jre".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("mvnw"));
            zos.write("#!/bin/sh\nexec java -jar .mvn/wrapper.jar".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    void getSubmissionTree_studentOwner_returnsTree() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);
        SubmissionProperties props = mock(SubmissionProperties.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());
        when(props.presignedUrlExpiryMinutes()).thenReturn(10L);

        SubmissionService service = newService(repo, client, rustfs, props);

        SubmissionZipTreeResponse tree = service.getSubmissionTree(SUBMISSION_ID, STUDENT_ID);

        assertNotNull(tree);
        assertEquals(SUBMISSION_ID, tree.getSubmissionId());
        assertNotNull(tree.getRoot());
        assertEquals("root", tree.getRoot().getName());
        assertEquals("folder", tree.getRoot().getType());

        // Directory zip entries end with "/", so their display name must come from
        // the stripped path — an empty name left folders untitled in the UI.
        // Review: 2026-11-05, zip viewer tree folder names.
        ZipTreeNode explicitFolder = tree.getRoot().getChildren().stream()
                .filter(node -> "folder".equals(node.getType()) && "folder".equals(node.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(explicitFolder.getChildren().isEmpty());

        ZipTreeNode implicitFolder = tree.getRoot().getChildren().stream()
                .filter(node -> "folder".equals(node.getType()) && "src".equals(node.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Main.java", implicitFolder.getChildren().get(0).getName());
        verify(rustfs).downloadObject("subs/" + SUBMISSION_ID + ".zip");
        verifyNoInteractions(client);
    }

    @Test
    void getSubmissionTree_lecturerOwnerOfClass_returnsTree() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(client.getAssignmentOwner(ASSIGNMENT_ID))
                .thenReturn(new AssignmentOwnerResponse(LECTURER_ID));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipTreeResponse tree = service.getSubmissionTree(SUBMISSION_ID, LECTURER_ID);

        assertNotNull(tree);
        assertEquals(SUBMISSION_ID, tree.getSubmissionId());
        verify(client).getAssignmentOwner(ASSIGNMENT_ID);
    }

    @Test
    void getSubmissionTree_notOwner_throwsNotFound() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(client.getAssignmentOwner(ASSIGNMENT_ID))
                .thenReturn(new AssignmentOwnerResponse(LECTURER_ID));

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSubmissionTree(SUBMISSION_ID, OTHER_ID));
    }

    @Test
    void getSubmissionTree_submissionNotFound_throwsNotFound() {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.empty());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSubmissionTree(SUBMISSION_ID, STUDENT_ID));
    }

    @Test
    void getSubmissionDownloadUrl_studentOwner_returnsPresignedUrl() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);
        SubmissionProperties props = mock(SubmissionProperties.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.generatePresignedDownloadUrl("subs/" + SUBMISSION_ID + ".zip"))
                .thenReturn("https://rustfs.example.com/presigned");
        when(props.presignedUrlExpiryMinutes()).thenReturn(15L);

        SubmissionService service = newService(repo, client, rustfs, props);

        SubmissionZipDownloadResponse resp = service.getSubmissionDownloadUrl(SUBMISSION_ID, STUDENT_ID);

        assertNotNull(resp);
        assertEquals(SUBMISSION_ID, resp.getSubmissionId());
        assertEquals("https://rustfs.example.com/presigned", resp.getDownloadUrl());
        assertEquals(15L, resp.getExpiresInMinutes());
        verifyNoInteractions(client);
    }

    @Test
    void getZipFileContent_textFile_returnsContent() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipFileResponse resp = service.getZipFileContent(SUBMISSION_ID, "src/Main.java", STUDENT_ID);

        assertNotNull(resp);
        assertFalse(resp.getIsBinary());
        assertEquals("src/Main.java", resp.getPath());
        assertNotNull(resp.getContent());
        assertTrue(resp.getContent().contains("public class Main"));
        assertFalse(resp.getTruncated());
        verifyNoInteractions(client);
    }

    @Test
    void getZipFileContent_binaryFile_returnsBinaryFlag() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipFileResponse resp = service.getZipFileContent(SUBMISSION_ID, "images/logo.png", STUDENT_ID);

        assertNotNull(resp);
        assertTrue(resp.getIsBinary());
        assertNull(resp.getContent());
        assertNotNull(resp.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void getZipFileContent_xmlFile_returnsTextContent() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipFileResponse resp = service.getZipFileContent(SUBMISSION_ID, "pom.xml", STUDENT_ID);

        // application/xml is not text/* but is still UTF-8 text and must be previewable.
        assertFalse(resp.getIsBinary());
        assertEquals("application/xml", resp.getMime());
        assertTrue(resp.getContent().contains("modelVersion"));
        verifyNoInteractions(client);
    }

    @Test
    void getZipFileContent_dockerfile_returnsTextContent() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipFileResponse resp = service.getZipFileContent(SUBMISSION_ID, "Dockerfile", STUDENT_ID);

        // No extension: the type comes from the file name.
        assertFalse(resp.getIsBinary());
        assertEquals("text/x-dockerfile", resp.getMime());
        assertTrue(resp.getContent().contains("FROM eclipse-temurin"));
        verifyNoInteractions(client);
    }

    @Test
    void getZipFileContent_mavenWrapper_returnsTextContent() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipFileResponse resp = service.getZipFileContent(SUBMISSION_ID, "mvnw", STUDENT_ID);

        assertFalse(resp.getIsBinary());
        assertEquals("text/x-shellscript", resp.getMime());
        assertTrue(resp.getContent().contains("#!/bin/sh"));
        verifyNoInteractions(client);
    }

    @Test
    void getZipFileContent_fileNotFound_throwsNotFound() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/" + SUBMISSION_ID + ".zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/" + SUBMISSION_ID + ".zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getZipFileContent(SUBMISSION_ID, "nonexistent.txt", STUDENT_ID));
    }

    @Test
    void getZipFileContent_submissionNotFound_throwsNotFound() {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.empty());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getZipFileContent(SUBMISSION_ID, "src/Main.java", STUDENT_ID));
    }

    @Test
    void getSubmissionTree_studentOwner_noCourseServiceCall() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/x.zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(rustfs.downloadObject("subs/x.zip")).thenReturn(buildZipBytes());

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        SubmissionZipTreeResponse tree = service.getSubmissionTree(SUBMISSION_ID, STUDENT_ID);
        assertNotNull(tree);
        verifyNoInteractions(client);
    }

    @Test
    void getSubmissionTree_lecturerNotOwner_throwsNotFound() throws Exception {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        CourseServiceClient client = mock(CourseServiceClient.class);
        RustFSService rustfs = mock(RustFSService.class);

        Submission sub = submission("subs/x.zip", STUDENT_ID);
        when(repo.findById(SUBMISSION_ID)).thenReturn(Optional.of(sub));
        when(client.getAssignmentOwner(ASSIGNMENT_ID))
                .thenReturn(new AssignmentOwnerResponse(LECTURER_ID));

        SubmissionService service = newService(repo, client, rustfs, mock(SubmissionProperties.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSubmissionTree(SUBMISSION_ID, OTHER_ID));
    }
}

package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactServiceTest {

    @TempDir
    Path tempDir;

    private ArtifactService serviceWithZip(byte[] zipBytes) {
        return serviceWithZipAndMaven(zipBytes,
                "https://example.invalid/maven-bin.zip");
    }

    private ArtifactService serviceWithZipAndMaven(byte[] zipBytes, String pinnedUrl) {
        ExecutorProperties props = new ExecutorProperties(
                tempDir.toString(), new ExecutorProperties.Container(1000, 2000),
                new ExecutorProperties.Reaper(30, 300000, 3),
                new ExecutorProperties.Maven(pinnedUrl));
        return new ArtifactService(null, null, props) {
            @Override
            protected InputStream fetchObject(String rustfsPath) {
                return new ByteArrayInputStream(zipBytes);
            }
        };
    }

    private static byte[] zipOf(String... namesAndBodies) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (int i = 0; i < namesAndBodies.length; i += 2) {
                zos.putNextEntry(new ZipEntry(namesAndBodies[i]));
                zos.write(namesAndBodies[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    void fetchWorkDir_extractsZipIntoSubmissionDir() throws IOException {
        byte[] zip = zipOf("docker-compose.yml", "services: {}", "app/Main.java", "class Main {}");
        UUID submissionId = UUID.randomUUID();

        Path workDir = serviceWithZip(zip).fetchWorkDir(submissionId, "submissions/x.zip");

        assertEquals(tempDir.resolve(submissionId.toString()), workDir);
        assertTrue(Files.isRegularFile(workDir.resolve("docker-compose.yml")));
        assertTrue(Files.isRegularFile(workDir.resolve("app/Main.java")));
    }

    @Test
    void fetchWorkDir_marksBuildWrappersExecutable() throws IOException {
        byte[] zip = zipOf("mvnw", "#!/bin/sh\necho hi", "Dockerfile", "FROM x");
        UUID submissionId = UUID.randomUUID();

        Path workDir = serviceWithZip(zip).fetchWorkDir(submissionId, "submissions/x.zip");

        assertTrue(Files.isExecutable(workDir.resolve("mvnw")));
    }

    @Test
    void fetchWorkDir_rewritesWrapperOnlyDistributionUrl() throws IOException {
        byte[] zip = zipOf(
                ".mvn/wrapper/maven-wrapper.properties",
                "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-bin.zip\ndistributionSha256Sum=abc123\n",
                "pom.xml", "<project/>");
        UUID submissionId = UUID.randomUUID();

        Path workDir = serviceWithZipAndMaven(zip, "https://example.invalid/maven-bin.zip")
                .fetchWorkDir(submissionId, "submissions/x.zip");

        String rewritten = Files.readString(workDir.resolve(".mvn/wrapper/maven-wrapper.properties"));
        assertTrue(rewritten.contains("distributionUrl=https://example.invalid/maven-bin.zip"));
        assertFalse(rewritten.contains("distributionSha256Sum"));
        assertFalse(rewritten.contains("maven-wrapper-distribution-"));
    }

    @Test
    void fetchWorkDir_leavesFullMavenDistributionUrlUntouched() throws IOException {
        String body = "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip\n";
        byte[] zip = zipOf(".mvn/wrapper/maven-wrapper.properties", body, "pom.xml", "<project/>");
        UUID submissionId = UUID.randomUUID();

        Path workDir = serviceWithZipAndMaven(zip, "https://example.invalid/maven-bin.zip")
                .fetchWorkDir(submissionId, "submissions/x.zip");

        assertEquals(body, Files.readString(workDir.resolve(".mvn/wrapper/maven-wrapper.properties")));
    }

    @Test
    void fetchWorkDir_blankPinnedUrlSkipsPatch() throws IOException {
        byte[] zip = zipOf(
                ".mvn/wrapper/maven-wrapper.properties",
                "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-bin.zip\n",
                "pom.xml", "<project/>");
        UUID submissionId = UUID.randomUUID();

        Path workDir = serviceWithZipAndMaven(zip, "  ")
                .fetchWorkDir(submissionId, "submissions/x.zip");

        assertTrue(Files.readString(workDir.resolve(".mvn/wrapper/maven-wrapper.properties"))
                .contains("maven-wrapper-distribution-"));
    }

    @Test
    void fetchWorkDir_rejectsZipSlipEntry() throws IOException {
        byte[] zip = zipOf("../../evil.txt", "pwned");
        UUID submissionId = UUID.randomUUID();

        ArtifactService service = serviceWithZip(zip);
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchWorkDir(submissionId, "submissions/x.zip"));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    void unzip_rejectsAbsoluteEntry() throws IOException {
        byte[] zip = zipOf("/abs.txt", "nope");
        Path dest = tempDir.resolve("dest");

        assertThrows(IllegalArgumentException.class,
                () -> ArtifactService.unzip(writeTemp(zip), dest));
    }

    private Path writeTemp(byte[] zip) throws IOException {
        Path file = tempDir.resolve("in.zip");
        Files.write(file, zip);
        return file;
    }
}

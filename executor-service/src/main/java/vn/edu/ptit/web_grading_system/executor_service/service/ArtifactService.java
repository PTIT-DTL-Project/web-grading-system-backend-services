package vn.edu.ptit.web_grading_system.executor_service.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.config.RustFsProperties;
import vn.edu.ptit.web_grading_system.executor_service.Constant;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads the submission zip from RustFS and unpacks it into the per-job
 * work directory. Zip-slip entries (../ or absolute paths) are rejected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactService
{
    private static final List<String> EXECUTABLE_WRAPPERS = List.of("mvnw", "gradlew");

    private final MinioClient minioClient;
    private final RustFsProperties rustFsProperties;
    private final ExecutorProperties executorProperties;

    public Path fetchWorkDir(UUID submissionId, String rustfsPath)
    {
        Path workDir = Path.of(executorProperties.tempDir(), submissionId.toString());
        try
        {
            Files.createDirectories(workDir);
            Path zipFile = workDir.resolve(Constant.Artifact.SUBMISSION_ZIP);
            try (InputStream in = fetchObject(rustfsPath))
            {
                Files.copy(in, zipFile, StandardCopyOption.REPLACE_EXISTING);
            }
            unzip(zipFile, workDir);
            restoreWrapperPermissions(workDir);
            patchWrapperDistributionUrl(workDir);
            log.info("Submission artifact ready: submission={}, dir={}", submissionId, workDir);
            return workDir;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(Constant.Message.FAILED_FETCH_ARTIFACT + rustfsPath, e);
        }
    }

    protected InputStream fetchObject(String rustfsPath) throws IOException
    {
        try
        {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(rustFsProperties.bucketName())
                    .object(rustfsPath)
                    .build());
        }
        catch (Exception e)
        {
            throw new IOException(Constant.Message.FAILED_DOWNLOAD_OBJECT + rustfsPath, e);
        }
    }

    static void unzip(Path zipFile, Path destDir) throws IOException
    {
        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis))
        {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null)
            {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir.normalize()))
                {
                    throw new IllegalArgumentException(Constant.Message.ZIP_ESCAPE_PREFIX + entry.getName());
                }
                if (entry.isDirectory())
                {
                    Files.createDirectories(target);
                }
                else
                {
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target))
                    {
                        zis.transferTo(out);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Zip extraction drops Unix mode bits, so build wrappers (755 in git)
     * land non-executable and the DinD {@code docker build} fails at
     * {@code RUN ./mvnw} with "Permission denied" (exit 126). Restore +x on
     * the known wrappers at the workdir root. Best-effort only — a chmod
     * failure must never fail grading; the docker build is the real gate.
     */
    static void restoreWrapperPermissions(Path workDir)
    {
        for (String name : EXECUTABLE_WRAPPERS)
        {
            Path wrapper = workDir.resolve(name);
            try
            {
                if (Files.isRegularFile(wrapper) && !Files.isExecutable(wrapper))
                {
                    Files.setPosixFilePermissions(wrapper,
                            PosixFilePermissions.fromString("rwxr-xr-x"));
                    log.debug("Restored executable bit: {}", wrapper);
                }
            }
            catch (Exception e)
            {
                log.warn("Could not chmod {}: {}", wrapper, e.getMessage());
            }
        }
    }

    /**
     * Rewrites a wrapper-only {@code distributionUrl} to the configured full
     * Maven distribution. Published {@code maven-wrapper-distribution}
     * artifacts ship scripts + wrapper jar but no Maven binaries, so any
     * submission pointing at one fails its build deterministically. Only URLs
     * containing that artifact name are touched; full Maven URLs, Gradle and
     * wrapper-less projects pass through unchanged. A stale
     * {@code distributionSha256Sum} is dropped alongside the rewrite since it
     * pins the old URL's checksum. Best-effort: never fails grading.
     *
     * @return true when the properties file was rewritten
     */
    boolean patchWrapperDistributionUrl(Path workDir)
    {
        ExecutorProperties.Maven maven = executorProperties.maven();
        String pinned = maven == null ? null : maven.pinnedDistributionUrl();
        if (pinned == null || pinned.isBlank())
        {
            return false;
        }
        Path props = workDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        if (!Files.isRegularFile(props))
        {
            return false;
        }
        try
        {
            List<String> lines = Files.readAllLines(props, StandardCharsets.UTF_8);
            boolean broken = lines.stream().anyMatch(ArtifactService::isWrapperOnlyDistributionUrl);
            if (!broken)
            {
                return false;
            }
            List<String> kept = new ArrayList<>(lines.size());
            for (String line : lines)
            {
                String trimmed = line.trim();
                if (!trimmed.startsWith("#") && trimmed.startsWith("distributionUrl="))
                {
                    kept.add("distributionUrl=" + pinned);
                }
                else if (!trimmed.startsWith("#") && trimmed.startsWith("distributionSha256Sum="))
                {
                    log.info("Dropped stale distributionSha256Sum for {}", props);
                }
                else
                {
                    kept.add(line);
                }
            }
            Files.write(props, kept, StandardCharsets.UTF_8);
            log.info("Rewrote maven-wrapper distributionUrl to {} (published wrapper distributions lack Maven binaries)", pinned);
            return true;
        }
        catch (Exception e)
        {
            log.debug("Could not patch {}: {}", props, e.getMessage());
            return false;
        }
    }

    private static boolean isWrapperOnlyDistributionUrl(String line)
    {
        String trimmed = line.trim();
        return !trimmed.startsWith("#") && trimmed.startsWith("distributionUrl=")
                && trimmed.contains("maven-wrapper-distribution-");
    }
}

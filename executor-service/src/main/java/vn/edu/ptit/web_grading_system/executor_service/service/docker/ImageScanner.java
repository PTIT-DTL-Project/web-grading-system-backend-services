package vn.edu.ptit.web_grading_system.executor_service.service.docker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.client.CourseInternalClient;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.entity.DockerImageState;
import vn.edu.ptit.web_grading_system.executor_service.entity.ImageScanStatus;
import vn.edu.ptit.web_grading_system.executor_service.repository.DockerImageStateRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MILLIS;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageScanner {

    private static final int WARM = 0, PULLED = 1, FAILED = 2;

    private final DockerImageGateway gateway;
    private final CourseInternalClient courseInternalClient;
    private final DockerImageStateRepository stateRepository;
    private final ExecutorProperties executorProperties;

    @Scheduled(fixedDelayString = "${executor.image-scan.interval-ms:300000}")
    public void scan() {
        ExecutorProperties.ImageScan cfg = executorProperties.imageScan();
        if (cfg == null || !cfg.enabled()) return;

        List<String> urls;
        try {
            urls = courseInternalClient.images();
        } catch (Exception e) {
            log.warn(Constant.ImageScan.FETCH_FAILED + "{}", e.getMessage());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int pruned = stateRepository.pruneBefore(
                now.minus(cfg.intervalMs() * 6, MILLIS), now);
        int present = 0, pulled = 0, failed = 0;
        String podId = podId();

        for (String url : urls) {
            int r = scanOne(url, cfg, now, podId);
            if (r == WARM) present++;
            else if (r == PULLED) pulled++;
            else failed++;
        }
        log.info(Constant.ImageScan.CYCLE_SUMMARY,
                present, pulled, failed, pruned, podId);
    }

    private int scanOne(String url, ExecutorProperties.ImageScan cfg,
                        OffsetDateTime now, String podId) {
        Optional<DockerImageState> row =
                stateRepository.findByImageUrlAndPodId(url, podId);
        if (row.isPresent() && row.get().getStatus() == ImageScanStatus.FAILED
                && row.get().getLastPulledAt() != null
                && !row.get().getLastPulledAt().isBefore(
                        now.minus(cfg.failBackoffMs(), MILLIS))) {
            return FAILED; // backoff: skip, do not touch the row
        }
        if (gateway.present(url)) {
            upsertWarm(url, podId, now, row);
            return WARM;
        }
        try {
            gateway.pull(url, Duration.ofMillis(cfg.pullTimeoutMs()));
            upsertPulled(url, podId, now, row);
            return PULLED;
        } catch (Exception e) {
            upsertFailed(url, podId, now, e.getMessage(), row);
            return FAILED;
        }
    }

    private void upsertWarm(String url, String podId, OffsetDateTime now,
                            Optional<DockerImageState> row) {
        if (row.isPresent()) {
            DockerImageState s = row.get();
            s.setStatus(ImageScanStatus.PULLED);
            s.setLastCheckedAt(now);
            s.setErrorMessage(null);
            stateRepository.save(s);
        } else {
            stateRepository.save(DockerImageState.builder()
                    .imageUrl(url).podId(podId).status(ImageScanStatus.PULLED)
                    .lastCheckedAt(now).build());
        }
    }

    private void upsertPulled(String url, String podId, OffsetDateTime now,
                              Optional<DockerImageState> row) {
        if (row.isPresent()) {
            DockerImageState s = row.get();
            s.setStatus(ImageScanStatus.PULLED);
            s.setLastCheckedAt(now);
            s.setLastPulledAt(now);
            s.setErrorMessage(null);
            stateRepository.save(s);
        } else {
            stateRepository.save(DockerImageState.builder()
                    .imageUrl(url).podId(podId).status(ImageScanStatus.PULLED)
                    .lastCheckedAt(now).lastPulledAt(now).build());
        }
    }

    private void upsertFailed(String url, String podId, OffsetDateTime now,
                              String message, Optional<DockerImageState> row) {
        if (row.isPresent()) {
            DockerImageState s = row.get();
            s.setStatus(ImageScanStatus.FAILED);
            s.setLastCheckedAt(now);
            s.setLastPulledAt(now);
            s.setErrorMessage(message);
            stateRepository.save(s);
        } else {
            stateRepository.save(DockerImageState.builder()
                    .imageUrl(url).podId(podId).status(ImageScanStatus.FAILED)
                    .lastCheckedAt(now).lastPulledAt(now)
                    .errorMessage(message).build());
        }
    }

    private String podId() {
        String h = System.getenv(Constant.ImageScan.ENV_POD_ID);
        if (h != null && !h.isBlank()) return h;
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (java.net.UnknownHostException e) {
            return Constant.ImageScan.POD_ID_FALLBACK;
        }
    }
}

package vn.edu.ptit.web_grading_system.executor_service.config;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "executor")
@Builder
public record ExecutorProperties(String tempDir, Container container, Reaper reaper, Maven maven,
        ImageScan imageScan) {

    public record Container(long startupTimeoutMs, long maxExecutionTimeMs) {
    }

    /**
     * Crash-recovery reaper. stale-after must exceed the longest possible job
     * (startup + execution timeouts) or live jobs get double-graded.
     */
    public record Reaper(long staleAfterMinutes, long intervalMs, int maxAttempts) {
    }

    /**
     * Maven toolchain pin for wrapper-based submissions. Published
     * maven-wrapper-distribution artifacts contain no Maven binaries, so the
     * executor rewrites such distributionUrls to this full distribution.
     * Blank disables the rewrite. Bump deliberately — grading must stay
     * reproducible, never resolve "latest" at runtime.
     */
    public record Maven(String pinnedDistributionUrl) {
    }

    /**
     * Axis-2 pre-pull scanner: warms this pod's DinD store with every
     * active library image. The prune horizon (6 x intervalMs) must
     * exceed failBackoffMs, or a row that is backing off gets pruned
     * early (harmless: it only costs one early retry).
     */
    @Builder
    public record ImageScan(boolean enabled, long intervalMs,
            long pullTimeoutMs, long failBackoffMs) {
    }
}

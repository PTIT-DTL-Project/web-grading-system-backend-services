package vn.edu.ptit.web_grading_system.executor_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "executor")
public record ExecutorProperties(String tempDir, Container container, Reaper reaper) {

    public record Container(long startupTimeoutMs, long maxExecutionTimeMs) {
    }

    /**
     * Crash-recovery reaper. stale-after must exceed the longest possible job
     * (startup + execution timeouts) or live jobs get double-graded.
     */
    public record Reaper(long staleAfterMinutes, long intervalMs, int maxAttempts) {
    }
}

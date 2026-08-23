package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "submission")
public record SubmissionProperties(long presignedUrlExpiryMinutes) {
}
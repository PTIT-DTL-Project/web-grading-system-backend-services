package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rustfs")
public record RustFsProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucketName) {
}
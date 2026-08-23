package vn.edu.ptit.web_grading_system.submission_service.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RustFSConfig {

    @Bean
    @Primary
    public MinioClient minioClient(RustFsProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    public MinioClient publicMinioClient(RustFsProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint() != null ? properties.publicEndpoint() : properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
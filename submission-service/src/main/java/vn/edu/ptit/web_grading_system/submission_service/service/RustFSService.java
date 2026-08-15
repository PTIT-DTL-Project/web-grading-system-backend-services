package vn.edu.ptit.web_grading_system.submission_service.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RustFSService {

    private final MinioClient minioClient;

    @Value("${rustfs.endpoint}")
    private String rustfsEndpoint;

    @Value("${rustfs.access-key}")
    private String accessKey;

    @Value("${rustfs.secret-key}")
    private String secretKey;

    @Value("${rustfs.bucket-name}")
    private String bucketName;

    @Value("${submission.presigned-url-expiry-minutes}")
    private long presignedUrlExpiryMinutes;

    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
                registerWebhookNotification();
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists: {}", bucketName, e);
            throw new RuntimeException("Failed to ensure bucket exists", e);
        }
    }

    public String generatePresignedUploadUrl(String objectName) {
        try {
            ensureBucketExists();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry((int) presignedUrlExpiryMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL for {}", objectName, e);
            throw new RuntimeException("Failed to generate presigned upload URL", e);
        }
    }

    public String generatePresignedDownloadUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned download URL for {}", objectName, e);
            throw new RuntimeException("Failed to generate presigned download URL", e);
        }
    }

    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            log.info("Deleted file from RustFS: {}/{}", bucketName, objectName);
        } catch (Exception e) {
            log.error("Failed to delete file from RustFS: {}", objectName, e);
        }
    }

    public InputStream getObject(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.error("Failed to get object from RustFS: {}", objectName, e);
            throw new RuntimeException("Failed to get object from RustFS", e);
        }
    }

    public String buildObjectName(UUID submissionId) {
        return "submissions/" + submissionId + ".zip";
    }

    private void registerWebhookNotification() {
        try {
            S3Client s3Client = S3Client.builder()
                    .endpointOverride(URI.create(rustfsEndpoint))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .forcePathStyle(true)
                    .build();

            NotificationConfiguration notificationConfig = NotificationConfiguration.builder()
                    .queueConfigurations(QueueConfiguration.builder()
                            .queueArn("arn:rustfs:sqs::primary:webhook")
                            .events(Event.S3_OBJECT_CREATED_PUT)
                            .filter(NotificationConfigurationFilter.builder()
                                    .key(S3KeyFilter.builder()
                                            .filterRules(
                                                    FilterRule.builder().name(FilterRuleName.PREFIX).value("submissions/").build(),
                                                    FilterRule.builder().name(FilterRuleName.SUFFIX).value(".zip").build()
                                            ).build())
                                    .build())
                            .build())
                    .build();

            s3Client.putBucketNotificationConfiguration(
                    PutBucketNotificationConfigurationRequest.builder()
                            .bucket(bucketName)
                            .notificationConfiguration(notificationConfig)
                            .build());

            log.info("RustFS webhook notification registered: bucket={}, arn=arn:rustfs:sqs::primary:webhook", bucketName);
            s3Client.close();
        } catch (Exception e) {
            log.error("Failed to register RustFS webhook notification: {}", e.getMessage());
        }
    }
}

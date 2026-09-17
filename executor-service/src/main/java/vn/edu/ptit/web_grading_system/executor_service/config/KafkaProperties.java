package vn.edu.ptit.web_grading_system.executor_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.kafka")
public record KafkaProperties(
    String bootstrapServers,
    String securityProtocol,
    String saslMechanism,
    String saslJaasConfig,
    String sslEndpointIdentificationAlgorithm,
    String sslTruststoreType,
    String sslTruststoreLocation,
    String groupId,
    boolean enableAutoCommit,
    String autoOffsetReset,
    int maxPollRecords,
    String ackMode
) {}

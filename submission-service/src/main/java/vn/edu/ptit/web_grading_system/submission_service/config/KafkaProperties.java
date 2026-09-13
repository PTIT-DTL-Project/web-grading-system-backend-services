package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.kafka")
public record KafkaProperties(
    String bootstrapServers,
    Properties properties,
    Producer producer
) {
    public record Properties(Security security, Sasl sasl, Ssl ssl) {}
    public record Security(String protocol) {}
    public record Sasl(String mechanism, Jaas jaas) {}
    public record Jaas(String config) {}
    public record Ssl(Endpoint endpoint, Truststore truststore) {}
    public record Endpoint(Identification identification) {}
    public record Identification(String algorithm) {}
    public record Truststore(String type, String location) {}
    public record Producer(String acks, int retries) {}
}

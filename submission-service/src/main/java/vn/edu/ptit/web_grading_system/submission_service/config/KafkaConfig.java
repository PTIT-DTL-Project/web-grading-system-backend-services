package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private final KafkaProperties props;

    public KafkaConfig(KafkaProperties props) { this.props = props; }

    @Bean
    public ProducerFactory<String, WgsEvent<?>> wgsProducerFactory() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("bootstrap.servers", props.bootstrapServers());
        String jaasConfig = props.saslJaasConfig();
        if (jaasConfig != null && !jaasConfig.isBlank()) {
            cfg.put("sasl.jaas.config", jaasConfig);
        }
        cfg.put("security.protocol",
                props.securityProtocol() != null ? props.securityProtocol() : "SASL_SSL");
        cfg.put("sasl.mechanism",
                props.saslMechanism() != null ? props.saslMechanism() : "SCRAM-SHA-256");
        cfg.put("ssl.endpoint.identification.algorithm",
                props.sslEndpointIdentificationAlgorithm() != null
                        ? props.sslEndpointIdentificationAlgorithm() : "https");
        cfg.put("ssl.truststore.type",
                props.sslTruststoreType() != null ? props.sslTruststoreType() : "PEM");
        cfg.put("ssl.truststore.location",
                props.sslTruststoreLocation() != null ? props.sslTruststoreLocation() : "docker/kafka-ca.pem");
        cfg.put("acks", props.acks() != null ? props.acks() : "1");
        cfg.put("retries", props.retries());
        return new DefaultKafkaProducerFactory<>(cfg, new StringSerializer(), new JsonSerializer<>());
    }

    @Bean
    public KafkaTemplate<String, WgsEvent<?>> wgsKafkaTemplate() {
        return new KafkaTemplate<>(wgsProducerFactory());
    }
}

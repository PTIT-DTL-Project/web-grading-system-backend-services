package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:SASL_SSL}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:SCRAM-SHA-256}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.properties.ssl.endpoint.identification.algorithm:https}")
    private String sslEndpointIdentificationAlgorithm;

    @Value("${spring.kafka.properties.ssl.truststore.type:PEM}")
    private String sslTruststoreType;

    @Value("${spring.kafka.properties.ssl.truststore.location:docker/kafka-ca.pem}")
    private String sslTruststoreLocation;

    @Value("${spring.kafka.producer.acks:1}")
    private String acks;

    @Value("${spring.kafka.producer.retries:0}")
    private int retries;

    @Bean
    public ProducerFactory<String, WgsEvent<?>> wgsProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("security.protocol", securityProtocol);
        props.put("sasl.mechanism", saslMechanism);
        if (saslJaasConfig != null && !saslJaasConfig.isBlank()) {
            props.put("sasl.jaas.config", saslJaasConfig);
        }
        props.put("ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
        props.put("ssl.truststore.type", sslTruststoreType);
        props.put("ssl.truststore.location", sslTruststoreLocation);
        props.put("acks", acks);
        props.put("retries", retries);
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>());
    }

    @Bean
    public KafkaTemplate<String, WgsEvent<?>> wgsKafkaTemplate() {
        return new KafkaTemplate<>(wgsProducerFactory());
    }
}

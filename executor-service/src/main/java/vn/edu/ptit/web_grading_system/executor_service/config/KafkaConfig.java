package vn.edu.ptit.web_grading_system.executor_service.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Manual Kafka consumer wiring. Boot 4 ships no Kafka auto-configuration and
 * spring-kafka 4.x provides none either, so without these explicit beans plus
 * {@code @EnableKafka} on the application class the {@code @KafkaListener} in
 * {@code WgsEventsConsumer} is silently ignored (no endpoint, no consumer,
 * zero log output). Property keys mirror {@code application.yaml}
 * ({@code spring.kafka.*}) so env overrides keep working.
 */
@Configuration
public class KafkaConfig
{

    private final KafkaProperties props;

    public KafkaConfig(KafkaProperties props) { this.props = props; }

    @Bean
    public ConsumerFactory<String, String> consumerFactory()
    {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers());
        String jaasConfig = props.saslJaasConfig();
        if (jaasConfig != null && !jaasConfig.isBlank())
        {
            cfg.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }
        cfg.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                props.securityProtocol() != null ? props.securityProtocol() : "SASL_SSL");
        cfg.put(SaslConfigs.SASL_MECHANISM,
                props.saslMechanism() != null ? props.saslMechanism() : "SCRAM-SHA-256");
        cfg.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
                props.sslEndpointIdentificationAlgorithm() != null
                        ? props.sslEndpointIdentificationAlgorithm() : "https");
        cfg.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
                props.sslTruststoreType() != null ? props.sslTruststoreType() : "PEM");
        cfg.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                props.sslTruststoreLocation() != null ? props.sslTruststoreLocation() : "docker/kafka-ca.pem");
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG,
                props.groupId() != null ? props.groupId() : "executor-group");
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, props.enableAutoCommit());
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                props.autoOffsetReset() != null ? props.autoOffsetReset() : "earliest");
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, props.maxPollRecords());
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory()
    {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.valueOf(
                        props.ackMode() != null ? props.ackMode() : "RECORD"));
        return factory;
    }
}

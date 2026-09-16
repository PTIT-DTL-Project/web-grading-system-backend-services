package vn.edu.ptit.web_grading_system.executor_service.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KafkaConfigTest
{

    private KafkaConfig config()
    {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(config, "securityProtocol", "SASL_SSL");
        ReflectionTestUtils.setField(config, "saslMechanism", "SCRAM-SHA-256");
        ReflectionTestUtils.setField(config, "saslJaasConfig", "jaas-config");
        ReflectionTestUtils.setField(config, "sslEndpointIdentificationAlgorithm", "https");
        ReflectionTestUtils.setField(config, "sslTruststoreType", "PEM");
        ReflectionTestUtils.setField(config, "sslTruststoreLocation", "/abs/kafka-ca.pem");
        ReflectionTestUtils.setField(config, "groupId", "executor-group");
        ReflectionTestUtils.setField(config, "enableAutoCommit", true);
        ReflectionTestUtils.setField(config, "autoOffsetReset", "earliest");
        ReflectionTestUtils.setField(config, "maxPollRecords", 1);
        ReflectionTestUtils.setField(config, "ackMode", "RECORD");
        return config;
    }

    @Test
    void consumerFactoryMapsYamlKeys()
    {
        Map<String, Object> props = config().consumerFactory().getConfigurationProperties();

        assertEquals("kafka:9092", props.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("SASL_SSL", props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        assertEquals("SCRAM-SHA-256", props.get(SaslConfigs.SASL_MECHANISM));
        assertEquals("jaas-config", props.get(SaslConfigs.SASL_JAAS_CONFIG));
        assertEquals("https", props.get(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG));
        assertEquals("PEM", props.get(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG));
        assertEquals("/abs/kafka-ca.pem", props.get(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
        assertEquals("executor-group", props.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals(StringDeserializer.class, props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(true, props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals("earliest", props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals(1, props.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
    }

    @Test
    void blankJaasConfigIsOmitted()
    {
        KafkaConfig config = config();
        ReflectionTestUtils.setField(config, "saslJaasConfig", "  ");

        Map<String, Object> props = config.consumerFactory().getConfigurationProperties();

        assertFalse(props.containsKey(SaslConfigs.SASL_JAAS_CONFIG));
    }

    @Test
    void containerFactoryHonorsAckMode()
    {
        KafkaConfig config = config();
        ConcurrentKafkaListenerContainerFactory<String, String> factory = config.kafkaListenerContainerFactory();

        assertEquals(ContainerProperties.AckMode.RECORD, factory.getContainerProperties().getAckMode());
        assertEquals(config.consumerFactory().getConfigurationProperties(),
                factory.getConsumerFactory().getConfigurationProperties());
    }

    @Test
    void invalidAckModeFailsFast()
    {
        KafkaConfig config = config();
        ReflectionTestUtils.setField(config, "ackMode", "NOPE");

        assertThrows(IllegalArgumentException.class, config::kafkaListenerContainerFactory);
    }
}

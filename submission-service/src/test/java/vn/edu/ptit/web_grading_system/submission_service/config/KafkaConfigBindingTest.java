package vn.edu.ptit.web_grading_system.submission_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaConfigBindingTest {

    @Configuration
    @EnableConfigurationProperties(KafkaProperties.class)
    static class BindingConfig {
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(BindingConfig.class);

    private static String[] fullKafkaProps() {
        return new String[] {
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.kafka.properties.security.protocol=SASL_SSL",
                "spring.kafka.properties.sasl.mechanism=SCRAM-SHA-256",
                "spring.kafka.properties.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username=\"u\" password=\"p\";",
                "spring.kafka.properties.ssl.endpoint.identification.algorithm=https",
                "spring.kafka.properties.ssl.truststore.type=PEM",
                "spring.kafka.properties.ssl.truststore.location=docker/kafka-ca.pem",
                "spring.kafka.producer.acks=all",
                "spring.kafka.producer.retries=3",
        };
    }

    @Test
    void fullYaml_bindsScramCredentialsAndProducerTuning() {
        runner.withPropertyValues(fullKafkaProps()).run(ctx -> {
            KafkaProperties props = ctx.getBean(KafkaProperties.class);
            assertEquals("SASL_SSL", props.properties().security().protocol());
            assertTrue(props.properties().sasl().jaas().config().contains("ScramLoginModule"));
            assertEquals("all", props.producer().acks());
            assertEquals(3, props.producer().retries());

            Map<String, Object> cfg = ((DefaultKafkaProducerFactory<?, ?>)
                    new KafkaConfig(props).wgsProducerFactory()).getConfigurationProperties();
            assertTrue(String.valueOf(cfg.get("sasl.jaas.config")).contains("ScramLoginModule"));
            assertEquals("all", cfg.get("acks"));
            assertEquals(3, cfg.get("retries"));
        });
    }

    @Test
    void missingSubtrees_fallBackToDefaultsWithoutNpe() {        runner.withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092").run(ctx -> {
            KafkaProperties props = ctx.getBean(KafkaProperties.class);
            assertEquals("SASL_SSL", props.properties().security().protocol());
            assertEquals("all", props.producer().acks());
            assertEquals(3, props.producer().retries());

            Map<String, Object> cfg = ((DefaultKafkaProducerFactory<?, ?>)
                    new KafkaConfig(props).wgsProducerFactory()).getConfigurationProperties();
            assertEquals("SASL_SSL", cfg.get("security.protocol"));
            assertFalse(cfg.containsKey("sasl.jaas.config"));
        });
    }

    @Test
    void realApplicationYaml_bindsScramCredentialsAndProducerTuning() throws IOException {
        var sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        KafkaProperties props = new Binder(ConfigurationPropertySources.from(sources))
                .bind("spring.kafka", Bindable.of(KafkaProperties.class))
                .orElseThrow(() -> new IllegalStateException("spring.kafka missing from application.yaml"));

        assertEquals("SASL_SSL", props.properties().security().protocol());
        assertTrue(props.properties().sasl().jaas().config().contains("ScramLoginModule"));
        assertEquals("all", props.producer().acks());
        assertEquals(3, props.producer().retries());

        Map<String, Object> cfg = ((DefaultKafkaProducerFactory<?, ?>)
                new KafkaConfig(props).wgsProducerFactory()).getConfigurationProperties();
        assertTrue(String.valueOf(cfg.get("sasl.jaas.config")).contains("ScramLoginModule"));
        assertEquals("all", cfg.get("acks"));
        assertEquals(3, cfg.get("retries"));
    }
}

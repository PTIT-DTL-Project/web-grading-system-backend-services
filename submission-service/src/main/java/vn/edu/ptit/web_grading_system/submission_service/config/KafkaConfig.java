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
        String jaasConfig = props.properties().sasl().jaas().config();
        if (jaasConfig != null && !jaasConfig.isBlank()) {
            cfg.put("sasl.jaas.config", jaasConfig);
        }
        cfg.put("security.protocol", props.properties().security().protocol());
        cfg.put("sasl.mechanism", props.properties().sasl().mechanism());
        cfg.put("ssl.endpoint.identification.algorithm",
                props.properties().ssl().endpoint().identification().algorithm());
        cfg.put("ssl.truststore.type", props.properties().ssl().truststore().type());
        cfg.put("ssl.truststore.location", props.properties().ssl().truststore().location());
        cfg.put("acks", props.producer().acks());
        cfg.put("retries", props.producer().retries());
        return new DefaultKafkaProducerFactory<>(cfg, new StringSerializer(), new JsonSerializer<>());
    }

    @Bean
    public KafkaTemplate<String, WgsEvent<?>> wgsKafkaTemplate() {
        return new KafkaTemplate<>(wgsProducerFactory());
    }
}

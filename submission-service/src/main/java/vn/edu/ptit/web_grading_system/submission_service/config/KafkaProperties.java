package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.kafka")
public record KafkaProperties(
    String bootstrapServers,
    Properties properties,
    Producer producer
) {
    public KafkaProperties {
        if (bootstrapServers == null) bootstrapServers = "localhost:9092";
        if (properties == null) properties = new Properties(null, null, null);
        if (producer == null) producer = new Producer(null, null);
    }

    public record Properties(Security security, Sasl sasl, Ssl ssl) {
        public Properties {
            if (security == null) security = new Security(null);
            if (sasl == null) sasl = new Sasl(null, null);
            if (ssl == null) ssl = new Ssl(null, null);
        }
    }

    public record Security(String protocol) {
        public Security {
            if (protocol == null) protocol = "SASL_SSL";
        }
    }

    public record Sasl(String mechanism, Jaas jaas) {
        public Sasl {
            if (mechanism == null) mechanism = "SCRAM-SHA-256";
            if (jaas == null) jaas = new Jaas(null);
        }
    }

    public record Jaas(String config) {
        public Jaas {
            if (config == null) config = "";
        }
    }

    public record Ssl(Endpoint endpoint, Truststore truststore) {
        public Ssl {
            if (endpoint == null) endpoint = new Endpoint(null);
            if (truststore == null) truststore = new Truststore(null, null);
        }
    }

    public record Endpoint(Identification identification) {
        public Endpoint {
            if (identification == null) identification = new Identification(null);
        }
    }

    public record Identification(String algorithm) {
        public Identification {
            if (algorithm == null) algorithm = "https";
        }
    }

    public record Truststore(String type, String location) {
        public Truststore {
            if (type == null) type = "PEM";
            if (location == null) location = "docker/kafka-ca.pem";
        }
    }

    public record Producer(String acks, Integer retries) {
        public Producer {
            if (acks == null) acks = "all";
            if (retries == null) retries = 3;
        }
    }
}

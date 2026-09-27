package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DockerImageGatewayImplTest {

    private final DockerImageGateway gateway = new DockerImageGatewayImpl();

    @Test
    void presentReturnsFalseForUnknownImage() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        assertThat(gateway.present("wgs-never-exists:0.0.0")).isFalse();
    }

    @Test
    void pullThenPresentReturnsTrue() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        gateway.pull("alpine:3.20", Duration.ofMillis(120_000));
        assertThat(gateway.present("alpine:3.20")).isTrue();
    }
}

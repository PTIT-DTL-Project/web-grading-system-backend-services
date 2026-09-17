package vn.edu.ptit.web_grading_system.executor_service.service;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.time.Duration;

/**
 * Boots the patched student compose file against the DinD daemon
 * ({@code DOCKER_HOST=tcp://localhost:2375}). Testcontainers needs no docker
 * CLI in this image — only the TCP daemon.
 */
@Slf4j
@Service
public class DockerComposeRunner
{
    public record RunningCompose(ComposeContainer compose, String host, int port) implements AutoCloseable
    {
        @Override
        public void close()
        {
            try
            {
                compose.stop();
            }
            catch (Exception e)
            {
                log.warn(Constant.Message.COMPOSE_STOP_FAILED, e.getMessage());
            }
        }
    }

    public RunningCompose boot(DockerComposePatcher.EffectiveCompose effective, long startupTimeoutMs)
    {
        ComposeContainer compose = new ComposeContainer(new DockerImageName("docker:25.0.5"),
                effective.composeFile().toFile())
                .withExposedService(effective.serviceName(), effective.containerPort(),
                        Wait.forHttp("/").forStatusCodeMatching(status -> status < 500)
                                .withStartupTimeout(Duration.ofMillis(startupTimeoutMs * 2L)))
                .withBuild(true)
                .withStartupTimeout(Duration.ofMillis(startupTimeoutMs));
        compose.start();
        String host = compose.getServiceHost(effective.serviceName(), effective.containerPort());
        int port = compose.getServicePort(effective.serviceName(), effective.containerPort());
        log.info("Grading container up: service={}, host={}:{}", effective.serviceName(), host, port);
        return new RunningCompose(compose, host, port);
    }
}

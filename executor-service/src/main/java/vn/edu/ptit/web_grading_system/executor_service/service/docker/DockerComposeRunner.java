package vn.edu.ptit.web_grading_system.executor_service.service.docker;

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
            stopQuietly(compose);
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
        try
        {
            compose.start();
        }
        catch (RuntimeException e)
        {
            // A failed start (boot timeout, port clash) still created the project's
            // containers; Ryuk is disabled, so nothing else removes them and they
            // keep the allocated host port bound. The next job in this pod then dies
            // on "port is already allocated". Stop the project before rethrowing.
            // Review: 2026-10-29, "Bind for 0.0.0.0:20000 failed" incident.
            stopQuietly(compose);
            throw e;
        }
        String host = compose.getServiceHost(effective.serviceName(), effective.containerPort());
        int port = compose.getServicePort(effective.serviceName(), effective.containerPort());
        log.info("Grading container up: service={}, host={}:{}", effective.serviceName(), host, port);
        return new RunningCompose(compose, host, port);
    }

    private static void stopQuietly(ComposeContainer compose)
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

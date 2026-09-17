package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerComposePatcherTest {

    @TempDir
    Path workDir;

    private static final String STUDENT_COMPOSE = """
            services:
              app:
                build: .
                ports:
                  - "8080:8080"
              db:
                image: postgres:16
            """;

    @Test
    void studentStrategy_rewritesAppPortAndLimits() throws IOException {
        Files.writeString(workDir.resolve("docker-compose.yml"), STUDENT_COMPOSE);

        DockerComposePatcher.EffectiveCompose effective = DockerComposePatcher.writeEffectiveCompose(
                workDir, "STUDENT_DOCKER_COMPOSE", null, 23456, 8080, 0.5, 256);

        assertEquals("app", effective.serviceName());
        assertEquals(8080, effective.containerPort());
        Map<String, Object> services = servicesOf(effective.composeFile());
        assertEquals(List.of("23456:8080"), serviceOf(services, "app").get("ports"));
        assertEquals(Map.of("limits", Map.of("cpus", "0.5", "memory", "256M")),
                deployOf(services, "app").get("resources"));
        assertEquals(Map.of("limits", Map.of("cpus", "0.5", "memory", "256M")),
                deployOf(services, "db").get("resources"));
    }

    @Test
    void studentStrategy_missingComposeFile_fails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DockerComposePatcher.writeEffectiveCompose(
                        workDir, "STUDENT_DOCKER_COMPOSE", null, 23456, 8080, 0.5, 256));
        assertTrue(e.getMessage().contains("docker-compose.yml not found"));
    }

    @Test
    void lecturerStrategy_writesTemplate() throws IOException {
        DockerComposePatcher.EffectiveCompose effective = DockerComposePatcher.writeEffectiveCompose(
                workDir, "LECTURER_DOCKER_COMPOSE", STUDENT_COMPOSE, 23456, 8080, null, null);

        assertTrue(Files.isRegularFile(effective.composeFile()));
        assertEquals(List.of("23456:8080"), serviceOf(servicesOf(effective.composeFile()), "app").get("ports"));
    }

    @Test
    void lecturerStrategy_blankTemplate_fails() {
        assertThrows(IllegalStateException.class,
                () -> DockerComposePatcher.writeEffectiveCompose(
                        workDir, "LECTURER_DOCKER_COMPOSE", "  ", 23456, 8080, null, null));
    }

    @Test
    void privilegedService_rejected() throws IOException {
        Files.writeString(workDir.resolve("docker-compose.yml"), """
                services:
                  app:
                    image: evil:latest
                    privileged: true
                """);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DockerComposePatcher.writeEffectiveCompose(
                        workDir, "STUDENT_DOCKER_COMPOSE", null, 23456, 8080, null, null));
        assertTrue(e.getMessage().contains("privileged"));
    }

    @Test
    void dockerSockMount_rejected() throws IOException {
        Files.writeString(workDir.resolve("docker-compose.yml"), """
                services:
                  app:
                    image: evil:latest
                    volumes:
                      - /var/run/docker.sock:/var/run/docker.sock
                """);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DockerComposePatcher.writeEffectiveCompose(
                        workDir, "STUDENT_DOCKER_COMPOSE", null, 23456, 8080, null, null));
        assertTrue(e.getMessage().contains("docker.sock"));
    }

    @Test
    void serviceWithoutPorts_fallsBackToFirstService() throws IOException {
        Files.writeString(workDir.resolve("docker-compose.yml"), """
                services:
                  app:
                    build: .
                """);
        DockerComposePatcher.EffectiveCompose effective = DockerComposePatcher.writeEffectiveCompose(
                workDir, "STUDENT_DOCKER_COMPOSE", null, 23456, 8080, null, null);
        assertEquals("app", effective.serviceName());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicesOf(Path composeFile) throws IOException {
        try (InputStream in = Files.newInputStream(composeFile)) {
            return (Map<String, Object>) ((Map<String, Object>) new Yaml().load(in)).get("services");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serviceOf(Map<String, Object> services, String name) {
        return (Map<String, Object>) services.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deployOf(Map<String, Object> services, String name) {
        return (Map<String, Object>) serviceOf(services, name).get("deploy");
    }
}

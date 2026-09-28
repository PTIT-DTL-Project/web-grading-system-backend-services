package vn.edu.ptit.web_grading_system.executor_service.service.docker;

import java.time.Duration;
import vn.edu.ptit.web_grading_system.executor_service.exception.ImagePullException;

/**
 * Thin boundary over the DinD daemon ({@code DOCKER_HOST=tcp://localhost:2375},
 * shared with {@link DockerComposeRunner}). Mirrors the gateway pattern
 * reserved in the Axis-2 seam so grading-time inspection can reuse the
 * same implementation without re-parsing YAML.
 */
public interface DockerImageGateway {

    /** True when {@code imageRef} exists in this pod's local DinD store. */
    boolean present(String imageRef);

    /** Pull {@code imageRef} bounded by {@code timeout}; throws
     *  {@link ImagePullException} on failure or timeout. */
    void pull(String imageRef, Duration timeout) throws ImagePullException;
}

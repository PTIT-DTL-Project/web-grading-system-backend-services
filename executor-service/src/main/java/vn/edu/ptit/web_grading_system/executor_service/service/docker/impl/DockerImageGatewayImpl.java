package vn.edu.ptit.web_grading_system.executor_service.service.docker.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import org.springframework.stereotype.Service;
import org.testcontainers.DockerClientFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import vn.edu.ptit.web_grading_system.executor_service.exception.ImagePullException;
import vn.edu.ptit.web_grading_system.executor_service.service.docker.DockerImageGateway;

@Service
public class DockerImageGatewayImpl implements DockerImageGateway {

    private DockerClient dockerClient;

    private DockerClient client() {
        if (dockerClient == null) {
            dockerClient = DockerClientFactory.instance().client();
        }
        return dockerClient;
    }

    @Override
    public boolean present(String imageRef) {
        try {
            client().inspectImageCmd(imageRef).exec();
            return true;
        } catch (Exception e) { // NotFoundException -> absent; daemon-down or auth
            return false;          // also degrades here so pull() records a FAILED
        }                          // row instead of aborting the whole cycle (PR #21)
    }

    @Override
    public void pull(String imageRef, Duration timeout) throws ImagePullException {
        try {
            PullImageCmd cmd = client().pullImageCmd(imageRef);
            PullImageResultCallback cb = new PullImageResultCallback();
            cmd.exec(cb);
            boolean completed = cb.awaitCompletion(timeout.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new ImagePullException(
                        "pull timed out after " + timeout.toMinutes()
                                + "m for " + imageRef);
            }
        } catch (ImagePullException e) {
            throw e;
        } catch (Exception e) {
            throw new ImagePullException(
                    "pull failed for " + imageRef + ": " + e.getMessage(), e);
        }
    }
}

package vn.edu.ptit.web_grading_system.executor_service.service.grading;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import vn.edu.ptit.web_grading_system.executor_service.exception.ImagePullException;
import vn.edu.ptit.web_grading_system.executor_service.service.docker.DockerImageGateway;

/** Pure unit tests for {@link GradingOrchestrator#ensureImages(List, long)},
 *  exercised through reflection the same way {@code invokeScan} exercises
 *  {@code scanDbRequirements}. Each test isolates a single branch. */
class ImageEnsureTest {

    private static final String URL = "ghcr.io/org/app:v1.0";

    private static GradingOrchestrator orch(DockerImageGateway g) {
        Mockito.when(g.present(Mockito.any())).thenReturn(true);
        return new GradingOrchestrator(null, null, null, null, null, null, null,
                null, null, null, new ObjectMapper(), null, null, null, g);
    }

    @Test
    void presentImage_isSkipped() throws Exception {
        DockerImageGateway g = Mockito.mock(DockerImageGateway.class);
        GradingOrchestrator o = orch(g);
        Method m = GradingOrchestrator.class.getDeclaredMethod("ensureImages",
                List.class, long.class);
        m.setAccessible(true);
        m.invoke(o, List.of(URL), 600000L);
        verify(g, never()).pull(any(), any());
    }

    @Test
    void absentImage_isPulledWithConfiguredTimeout() throws Exception {
        DockerImageGateway g = Mockito.mock(DockerImageGateway.class);
        GradingOrchestrator o = orch(g);
        Mockito.when(g.present(Mockito.any())).thenReturn(false);
        Method m = GradingOrchestrator.class.getDeclaredMethod("ensureImages",
                List.class, long.class);
        m.setAccessible(true);
        m.invoke(o, List.of(URL), 600000L);
        ArgumentCaptor<Duration> cap = ArgumentCaptor.forClass(Duration.class);
        verify(g).pull(eq(URL), cap.capture());
        assertThat(cap.getValue())
                .isEqualTo(Duration.ofMillis(600000));
    }

    @Test
    void pullFailure_throwsNamingTheImage() throws Exception {
        DockerImageGateway g = Mockito.mock(DockerImageGateway.class);
        GradingOrchestrator o = orch(g);
        Mockito.when(g.present(Mockito.any())).thenReturn(false);
        Mockito.doThrow(new ImagePullException("rate limited"))
                .when(g).pull(Mockito.any(), Mockito.any());
        Method m = GradingOrchestrator.class.getDeclaredMethod("ensureImages",
                List.class, long.class);
        m.setAccessible(true);
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
                () -> m.invoke(o, List.of(URL), 600000L));
        assertInstanceOf(ImagePullException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains(URL));
    }
}

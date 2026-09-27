package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.client.CourseInternalClient;
import vn.edu.ptit.web_grading_system.executor_service.entities.DockerImageState;
import vn.edu.ptit.web_grading_system.executor_service.entities.ImageScanStatus;
import vn.edu.ptit.web_grading_system.executor_service.repository.DockerImageStateRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ImageScannerTest {

    private static final String URL = "ghcr.io/org/app:v1.0";
    private static final String POD = "scanner-pod";

    private static ExecutorProperties props(boolean enabled,
            long intervalMs, long pullTimeoutMs, long failBackoffMs) {
        return ExecutorProperties.builder()
                .tempDir("tmp")
                .container(new ExecutorProperties.Container(1000, 2000))
                .reaper(new ExecutorProperties.Reaper(30, 300000, 3))
                .maven(new ExecutorProperties.Maven(null))
                .imageScan(new ExecutorProperties.ImageScan(
                        enabled, intervalMs, pullTimeoutMs, failBackoffMs))
                .build();
    }

    private ImageScanner scanner(DockerImageGateway g, CourseInternalClient c,
            DockerImageStateRepository r, ExecutorProperties p) {
        return new ImageScanner(g, c, r, p);
    }

    @Test
    void disabledSkipsCycle() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        scanner(g, c, r, props(false, 300000, 600000, 600000)).scan();
        verifyNoInteractions(g, c, r);
    }

    @Test
    void fetchFailureAbortsCycle() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenThrow(new RuntimeException("down"));
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        verify(c).images();
        verifyNoInteractions(g);
        verify(r, never()).pruneBefore(any(), any());
        verify(r, never()).save(any());
    }

    @Test
    void presentImageMarksPulledWithoutPull() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenReturn(List.of(URL));
        Mockito.when(g.present(URL)).thenReturn(true);
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        verify(g, never()).pull(any(), any());
        verify(r).save(Mockito.argThat(s ->
                s.getStatus() == ImageScanStatus.PULLED
                        && s.getLastPulledAt() == null));
    }

    @Test
    void absentImagePullsWithConfiguredTimeout() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenReturn(List.of(URL));
        Mockito.when(g.present(URL)).thenReturn(false);
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        verify(g).pull(eq(URL), Mockito.any());
        verify(r).save(Mockito.argThat(s ->
                s.getStatus() == ImageScanStatus.PULLED
                        && s.getLastPulledAt() != null));
    }

    @Test
    void pullFailureMarksFailedAndContinues() {
        String url2 = "ghcr.io/org/other:v2";
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenReturn(List.of(URL, url2));
        Mockito.when(g.present(URL)).thenReturn(false);
        Mockito.when(g.present(url2)).thenReturn(false);
        Mockito.doThrow(new ImagePullException("timeout"))
                .when(g).pull(eq(URL), any());
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        verify(g).pull(eq(URL), any());
        verify(g).pull(eq(url2), any());
        verify(r).save(Mockito.argThat(s ->
                s.getStatus() == ImageScanStatus.FAILED));
        verify(r).save(Mockito.argThat(s ->
                s.getStatus() == ImageScanStatus.PULLED));
    }

    @Test
    void failedRowWithinBackoffSkipped() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenReturn(List.of(URL));
        Mockito.when(r.findByImageUrlAndPodId(eq(URL), anyString()))
                .thenReturn(Optional.of(DockerImageState.builder()
                        .imageUrl(URL).podId("any-pod").status(ImageScanStatus.FAILED)
                        .lastPulledAt(OffsetDateTime.now().minusMinutes(1))
                        .build()));
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        verify(g, never()).present(URL);
        verify(r, never()).save(any());
    }

    @Test
    void failedRowAfterBackoffRetried() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenReturn(List.of(URL));
        Mockito.when(r.findByImageUrlAndPodId(eq(URL), anyString()))
                .thenReturn(Optional.of(DockerImageState.builder()
                        .imageUrl(URL).podId("any-pod").status(ImageScanStatus.FAILED)
                        .lastPulledAt(OffsetDateTime.now().minusMinutes(20))
                        .build()));
        Mockito.when(g.present(URL)).thenReturn(true);
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        verify(g).present(URL);
        verify(r).save(Mockito.argThat(s ->
                s.getStatus() == ImageScanStatus.PULLED));
    }

    @Test
    void prunesStaleRowsAtSixIntervals() {
        DockerImageGateway g = mock(DockerImageGateway.class);
        CourseInternalClient c = mock(CourseInternalClient.class);
        DockerImageStateRepository r = mock(DockerImageStateRepository.class);
        Mockito.when(c.images()).thenReturn(List.of());
        scanner(g, c, r, props(true, 300000, 600000, 600000)).scan();
        ArgumentCaptor<OffsetDateTime> cutoff =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> nowCaptor =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(r).pruneBefore(cutoff.capture(), nowCaptor.capture());
        assertThat(cutoff.getValue())
                .isBetween(nowCaptor.getValue().minusMinutes(31),
                        nowCaptor.getValue().minusMinutes(29));
    }
}

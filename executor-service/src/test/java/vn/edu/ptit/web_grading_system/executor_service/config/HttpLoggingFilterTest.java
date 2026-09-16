package vn.edu.ptit.web_grading_system.executor_service.config;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import vn.edu.ptit.web_grading_system.executor_service.entities.HttpLog;
import vn.edu.ptit.web_grading_system.executor_service.entities.HttpLogDirection;
import vn.edu.ptit.web_grading_system.executor_service.service.HttpLogService;

class HttpLoggingFilterTest {

    private HttpLogService httpLogService;
    private HttpLoggingFilter filter;

    @BeforeEach
    void setUp() {
        httpLogService = Mockito.mock(HttpLogService.class);
        when(httpLogService.sanitizeUrl(any())).thenAnswer(a -> a.getArgument(0));
        when(httpLogService.headersToJson(any())).thenReturn(null);
        when(httpLogService.truncate(nullable(byte[].class))).thenAnswer(a -> {
            byte[] body = a.getArgument(0);
            return body == null || body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
        });
        when(httpLogService.isFileContentType(nullable(String.class))).thenReturn(false);
        filter = new HttpLoggingFilter(httpLogService, "test-service");
    }

    @Test
    void logsInboundRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/grading-jobs/abc/reset");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        request.setQueryString("page=0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).getWriter().write("{\"ok\":true}");
            ((HttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        HttpLog logged = captureLog();
        assertEquals(HttpLogDirection.INBOUND, logged.getDirection());
        assertEquals("test-service", logged.getServiceName());
        assertEquals("POST", logged.getMethod());
        assertTrue(logged.getUrl().contains("/api/v1/internal/grading-jobs/abc/reset?page=0"));
        assertEquals("{\"name\":\"x\"}", logged.getRequestBody());
        assertEquals(201, logged.getStatusCode());
        assertEquals("{\"ok\":true}", logged.getResponseBody());
        assertTrue(logged.getDurationMs() > 0);
        // Response body must reach the client.
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void logsInternalPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/grading-jobs/abc/reset");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        assertEquals(HttpLogDirection.INBOUND, captureLog().getDirection());
    }

    @Test
    void skipsExcludedPaths() throws Exception {
        String[] paths = {"/actuator/prometheus", "/v3/api-docs", "/v3/api-docs/springdoc.json",
                "/swagger-ui/index.html", "/swagger-ui.html", "/health", "/version"};
        for (String path : paths) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
        }
        verify(httpLogService, never()).save(any());
    }

    @Test
    void skipsFileUploadBody() throws Exception {
        when(httpLogService.isFileContentType("multipart/form-data; boundary=---")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/grading-jobs");
        request.setContentType("multipart/form-data; boundary=---");
        request.setContent("binary-bytes".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        final Object[] seen = new Object[1];
        FilterChain chain = (req, res) -> {
            seen[0] = req;
            ((HttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        HttpLog logged = captureLog();
        assertNull(logged.getRequestBody());
        // Upload must not be wrapped for caching.
        assertSame(request, seen[0]);
    }

    @Test
    void saveFailureDoesNotBreakResponse() throws Exception {
        Mockito.doThrow(new RuntimeException("db down")).when(httpLogService).save(any());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/grading-jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).getWriter().write("ok");
            ((HttpServletResponse) res).setStatus(200);
        });

        assertEquals("ok", response.getContentAsString());
    }

    private HttpLog captureLog() {
        ArgumentCaptor<HttpLog> captor = ArgumentCaptor.forClass(HttpLog.class);
        verify(httpLogService).save(captor.capture());
        return captor.getValue();
    }
}

package vn.edu.ptit.web_grading_system.result_service.config;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.result_service.entities.HttpLog;
import vn.edu.ptit.web_grading_system.result_service.entities.HttpLogDirection;
import vn.edu.ptit.web_grading_system.result_service.service.HttpLogService;

@Slf4j
@Component
public class HttpLogFilter extends OncePerRequestFilter {

    private static final int MAX_CAPTURE_BYTES = 20_000;

    private final HttpLogService httpLogService;

    @Value("${spring.application.name}")
    private String serviceName;

    public HttpLogFilter(HttpLogService httpLogService) {
        this.httpLogService = httpLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!(request instanceof ContentCachingRequestWrapper)) {
            request = new ContentCachingRequestWrapper(request, MAX_CAPTURE_BYTES);
        }
        if (!(response instanceof CappedContentCachingResponseWrapper)) {
            response = new CappedContentCachingResponseWrapper(response, MAX_CAPTURE_BYTES);
        }

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                ((CappedContentCachingResponseWrapper) response).copyBodyToResponse();
            } catch (Exception e) {
                log.warn("Could not copy response body: {}", e.getMessage());
            }
            saveLog((ContentCachingRequestWrapper) request, (CappedContentCachingResponseWrapper) response, start);
        }
    }

    private void saveLog(ContentCachingRequestWrapper request, CappedContentCachingResponseWrapper response, long start) {
        try {
            if (httpLogService.isFileContentType(request.getContentType())
                    || httpLogService.isFileContentType(response.getContentType())) {
                return;
            }
            HttpLog httpLog = HttpLog.builder()
                    .serviceName(serviceName)
                    .direction(HttpLogDirection.INBOUND)
                    .method(request.getMethod())
                    .url(httpLogService.sanitizeUrl(request.getRequestURI()
                            + (request.getQueryString() != null ? "?" + request.getQueryString() : "")))
                    .port(request.getLocalPort())
                    .requestHeaders(httpLogService.headersToJson(collectRequestHeaders(request)))
                    .requestBody(httpLogService.truncate(request.getContentAsByteArray()))
                    .statusCode(response.getStatus())
                    .responseHeaders(httpLogService.headersToJson(collectResponseHeaders(response)))
                    .responseBody(httpLogService.truncate(response.getContentAsByteArray()))
                    .durationMs((int) (System.currentTimeMillis() - start))
                    .build();
            httpLogService.save(httpLog);
        } catch (Exception e) {
            log.warn("Could not save http log: {}", e.getMessage());
        }
    }

    private Map<String, Collection<String>> collectRequestHeaders(HttpServletRequest request) {
        Map<String, Collection<String>> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private Map<String, Collection<String>> collectResponseHeaders(HttpServletResponse response) {
        Map<String, Collection<String>> headers = new HashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name));
        }
        return headers;
    }
}
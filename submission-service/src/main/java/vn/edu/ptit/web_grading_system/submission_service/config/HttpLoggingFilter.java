package vn.edu.ptit.web_grading_system.submission_service.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.submission_service.entities.HttpLog;
import vn.edu.ptit.web_grading_system.submission_service.entities.HttpLogDirection;
import vn.edu.ptit.web_grading_system.submission_service.service.HttpLogService;

// Persists one INBOUND http_log row per request (method, url, headers,
// bodies, status, duration). File bodies are skipped, probe/docs paths are
// excluded, and failures never break the request.
@Component
@Slf4j
public class HttpLoggingFilter extends OncePerRequestFilter {

    // Wrapper buffers at most this many bytes; HttpLogService.truncate() enforces
    // the 20KB stored-body cap on top, so the limit only bounds memory.
    private static final int CONTENT_CACHE_LIMIT = 32 * 1024;

    private final HttpLogService httpLogService;
    private final String serviceName;

    public HttpLoggingFilter(HttpLogService httpLogService,
            @Value("${spring.application.name}") String serviceName) {
        this.httpLogService = httpLogService;
        this.serviceName = serviceName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.endsWith("/health")
                || path.endsWith("/version");
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        // File uploads would be buffered in memory by the caching wrapper — skip it.
        boolean cacheRequest = !httpLogService.isFileContentType(request.getContentType());
        HttpServletRequest requestToUse = cacheRequest ? new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT) : request;
        ContentCachingResponseWrapper responseToUse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(requestToUse, responseToUse);
        } finally {
            try {
                saveLog(requestToUse, responseToUse, cacheRequest, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("Could not save http log: {}", e.getMessage());
            } finally {
                responseToUse.copyBodyToResponse();
            }
        }
    }

    private void saveLog(HttpServletRequest request, ContentCachingResponseWrapper response,
            boolean requestCached, long durationMs) {
        byte[] requestBody = requestCached
                ? ((ContentCachingRequestWrapper) request).getContentAsByteArray()
                : null;
        byte[] responseBody = httpLogService.isFileContentType(response.getContentType())
                ? null
                : response.getContentAsByteArray();
        String url = request.getRequestURL().toString();
        if (request.getQueryString() != null) {
            url += "?" + request.getQueryString();
        }
        HttpLog httpLog = HttpLog.builder()
                .serviceName(serviceName)
                .direction(HttpLogDirection.INBOUND)
                .method(request.getMethod())
                .url(httpLogService.sanitizeUrl(url))
                .port(request.getLocalPort())
                .requestHeaders(httpLogService.headersToJson(requestHeaders(request)))
                .requestBody(httpLogService.truncate(requestBody))
                .statusCode(response.getStatus())
                .responseHeaders(httpLogService.headersToJson(responseHeaders(response)))
                .responseBody(httpLogService.truncate(responseBody))
                .durationMs((int) durationMs)
                .build();
        httpLogService.save(httpLog);
    }

    private Map<String, Collection<String>> requestHeaders(HttpServletRequest request) {
        Map<String, Collection<String>> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            List<String> values = new ArrayList<>();
            Enumeration<String> valueHeaders = request.getHeaders(name);
            while (valueHeaders.hasMoreElements()) {
                values.add(valueHeaders.nextElement());
            }
            headers.put(name, values);
        }
        return headers;
    }

    private Map<String, Collection<String>> responseHeaders(ContentCachingResponseWrapper response) {
        Map<String, Collection<String>> headers = new HashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, response.getHeaders(name));
        }
        return headers;
    }
}

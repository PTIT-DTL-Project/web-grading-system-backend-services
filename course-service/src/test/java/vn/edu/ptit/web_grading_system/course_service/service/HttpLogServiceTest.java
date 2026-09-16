package vn.edu.ptit.web_grading_system.course_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import vn.edu.ptit.web_grading_system.course_service.repositories.HttpLogRepository;

class HttpLogServiceTest {

    private HttpLogService service;

    @Mock
    private HttpLogRepository httpLogRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(httpLogRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        service = new HttpLogService(httpLogRepository, new Gson());
    }

    @Test
    void sanitizeUrl_redactsXAmzSignature() {
        String url = "http://minio.example.com/bucket/obj"
                + "?X-Amz-Signature=abc123&X-Amz-Credential=key%2F20260101%2Fus-east-1%2Fs3%2Faws4_request"
                + "&other=keep";
        String result = service.sanitizeUrl(url);
        assertEquals("http://minio.example.com/bucket/obj"
                + "?X-Amz-Signature=REDACTED&X-Amz-Credential=REDACTED"
                + "&other=keep", result);
    }

    @Test
    void sanitizeUrl_preservesNonSensitiveParams() {
        String url = "http://minio.example.com/bucket/obj?page=0&sort=name";
        assertEquals(url, service.sanitizeUrl(url));
    }

    @Test
    void sanitizeUrl_nullReturnsNull() {
        assertNull(service.sanitizeUrl(null));
    }

    @Test
    void truncate_longBodyIsCutAt20Kb() {
        char[] chars = new char[30_000];
        java.util.Arrays.fill(chars, 'a');
        byte[] body = new String(chars).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String result = service.truncate(body);
        assertEquals(20_000 + "...(truncated)".length(), result.length());
        assertTrue(result.endsWith("...(truncated)"));
    }

    @Test
    void truncate_bodyWithinLimitKeptWhole() {
        byte[] body = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("hello", service.truncate(body));
    }

    @Test
    void truncate_nullReturnsNull() {
        assertNull(service.truncate(null));
    }

    @Test
    void truncate_emptyReturnsNull() {
        assertNull(service.truncate(new byte[0]));
    }

    @Test
    void headersToJson_dropsSensitiveHeaders() {
        Map<String, java.util.Collection<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("Cookie", List.of("session=abc"));
        headers.put("X-Api-Key", List.of("key123"));
        headers.put("Content-Type", List.of("application/json"));
        headers.put("X-Request-Id", List.of("abc-123"));
        String json = service.headersToJson(headers);
        Map<String, String> parsed = new Gson().fromJson(json, Map.class);
        assertEquals(Map.of("Content-Type", "application/json", "X-Request-Id", "abc-123"), parsed);
    }

    @Test
    void isFileContentType_fileTypes() {
        assertTrue(service.isFileContentType("multipart/form-data"));
        assertTrue(service.isFileContentType("application/octet-stream"));
        assertTrue(service.isFileContentType("image/png"));
        assertTrue(service.isFileContentType("application/zip"));
        assertTrue(service.isFileContentType("application/pdf"));
        assertFalse(service.isFileContentType("application/json"));
        assertFalse(service.isFileContentType("text/plain"));
        assertFalse(service.isFileContentType(null));
    }
}

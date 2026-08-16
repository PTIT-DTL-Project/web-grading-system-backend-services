package vn.edu.ptit.web_grading_system.submission_service.service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.submission_service.entities.HttpLog;
import vn.edu.ptit.web_grading_system.submission_service.repositories.HttpLogRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpLogService {

    private static final int MAX_CAPTURE_CHARS = 20_000;
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of("authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key");
    private static final Pattern SENSITIVE_QUERY_PARAM_PATTERN = Pattern.compile(
            "(?i)([?&](?:x-amz-algorithm|x-amz-credential|x-amz-date|x-amz-expires|x-amz-signedheaders"
                    + "|x-amz-signature|signature)=)[^&\\s\"']*");

    private final HttpLogRepository httpLogRepository;
    private final Gson gson = new Gson();

    public void save(HttpLog httpLog) {
        try {
            if (httpLog.getId() == null) {
                httpLog.setId(UUID.randomUUID());
            }
            httpLogRepository.save(httpLog);
        } catch (Exception e) {
            log.warn("Could not save http log", e);
        }
    }

    public String headersToJson(Map<String, ? extends Collection<String>> headers) {
        Map<String, String> sanitized = new HashMap<>();
        headers.forEach((name, values) -> {
            if (!SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                sanitized.put(name, String.join(", ", values));
            }
        });
        return sanitized.isEmpty() ? null : gson.toJson(sanitized);
    }

    public String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        return SENSITIVE_QUERY_PARAM_PATTERN.matcher(url).replaceAll("$1REDACTED");
    }

    public boolean isFileContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.split(";")[0].trim().toLowerCase();
        return type.startsWith("multipart/")
                || type.equals("application/octet-stream")
                || type.startsWith("image/")
                || type.startsWith("video/")
                || type.startsWith("audio/")
                || type.equals("application/zip")
                || type.equals("application/pdf");
    }

    public String truncate(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String value = new String(bytes, StandardCharsets.UTF_8);
        value = SENSITIVE_QUERY_PARAM_PATTERN.matcher(value).replaceAll("$1REDACTED");
        return value.length() <= MAX_CAPTURE_CHARS
                ? value
                : value.substring(0, MAX_CAPTURE_CHARS) + "...(truncated)";
    }
}
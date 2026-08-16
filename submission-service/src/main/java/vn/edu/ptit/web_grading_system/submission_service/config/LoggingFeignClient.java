package vn.edu.ptit.web_grading_system.submission_service.config;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import feign.Client;
import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.submission_service.entities.HttpLog;
import vn.edu.ptit.web_grading_system.submission_service.entities.HttpLogDirection;
import vn.edu.ptit.web_grading_system.submission_service.service.HttpLogService;

@Slf4j
public class LoggingFeignClient implements Client {

    private final HttpLogService httpLogService;
    private final String serviceName;
    private final Client delegate = new Client.Default(null, null);

    public LoggingFeignClient(HttpLogService httpLogService, String serviceName) {
        this.httpLogService = httpLogService;
        this.serviceName = serviceName;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        long start = System.currentTimeMillis();
        try {
            Response response = delegate.execute(request, options);
            byte[] responseBody = response.body() == null ? null : response.body().asInputStream().readAllBytes();
            if (!isFileCall(request, response)) {
                saveLog(request, response.status(), responseBody, System.currentTimeMillis() - start);
            }
            return response.toBuilder().body(responseBody).build();
        } catch (IOException e) {
            saveLog(request, null, null, System.currentTimeMillis() - start);
            throw e;
        }
    }

    private boolean isFileCall(Request request, Response response) {
        return httpLogService.isFileContentType(firstHeader(request.headers(), "content-type"))
                || httpLogService.isFileContentType(firstHeader(response.headers(), "content-type"));
    }

    private String firstHeader(Map<String, Collection<String>> headers, String name) {
        Collection<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.iterator().next();
    }

    private void saveLog(Request request, Integer statusCode, byte[] responseBody, long durationMs) {
        try {
            HttpLog httpLog = HttpLog.builder()
                    .serviceName(serviceName)
                    .direction(HttpLogDirection.OUTBOUND)
                    .method(request.httpMethod().name())
                    .url(request.url())
                    .port(portOf(request.url()))
                    .requestHeaders(httpLogService.headersToJson(request.headers()))
                    .requestBody(httpLogService.truncate(request.body()))
                    .statusCode(statusCode)
                    .responseBody(httpLogService.truncate(responseBody))
                    .durationMs((int) durationMs)
                    .build();
            httpLogService.save(httpLog);
        } catch (Exception e) {
            log.warn("Could not save http log: {}", e.getMessage());
        }
    }

    private Integer portOf(String url) {
        try {
            int port = URI.create(url).getPort();
            return port == -1 ? null : port;
        } catch (Exception e) {
            return null;
        }
    }
}
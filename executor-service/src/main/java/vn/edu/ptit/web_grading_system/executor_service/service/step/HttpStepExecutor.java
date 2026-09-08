package vn.edu.ptit.web_grading_system.executor_service.service.step;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.HttpLogService;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpStepExecutor implements StepExecutor {

    private final HttpLogService httpLogService;
    private final AssertionEngine assertionEngine;
    private final HttpClient httpClient;
    private final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    @Override
    public String type() {
        return "HTTP_REQUEST";
    }

    @Override
    public GradingStepResult execute(StepContext ctx) {
        long start = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        int attemptStatus = 0;
        String responseBody = null;
        Map<String, String> responseHeaders = Map.of();
        String errorMessage = null;
        List<AssertionEngine.AssertionDetail> assertionDetails = List.of();

        // substitute variables in config fields
        String method = ctx.variableContext().substitute(ctx.config().path("method").asText("GET"));
        String rawPath = ctx.variableContext().substitute(ctx.config().path("path").asText("/"));
        String url = "http://localhost:" + ctx.variableContext().get("app_port") + rawPath;

        // query_params
        if (ctx.config().hasNonNull("query_params") && ctx.config().get("query_params").isObject()) {
            StringBuilder queryPart = new StringBuilder();
            ctx.config().get("query_params").properties().forEach(e -> {
                String v = ctx.variableContext().substitute(e.getValue().asText(""));
                if (queryPart.length() > 0) queryPart.append("&");
                queryPart.append(e.getKey()).append("=").append(v);
            });
            if (queryPart.length() > 0) {
                url += (url.contains("?") ? "&" : "?") + queryPart;
            }
        }

        // headers
        Map<String, String> headers = new HashMap<>();
        if (ctx.config().hasNonNull("headers") && ctx.config().get("headers").isObject()) {
            ctx.config().get("headers").properties().forEach(e -> {
                String v = ctx.variableContext().substitute(e.getValue().asText(""));
                headers.put(e.getKey(), v);
            });
        }

        // body
        String bodyStr = null;
        if (ctx.config().hasNonNull("body")) {
            // body may be object/array - substitute inside stringified form already handled per-field above
            // For simplicity, substitute the stringified body
            bodyStr = ctx.variableContext().substitute(ctx.config().get("body").toString());
            // if original body was already substituted per-field, this covers it
            // If body contains variables, they are now replaced
        }

        int timeoutMs = ctx.config().path("timeoutMs").asInt(ctx.timeoutMs() != null ? ctx.timeoutMs() : 30000);

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs));

            // method + body
            String m = method.toUpperCase();
            HttpRequest.BodyPublisher publisher = bodyStr != null
                    ? HttpRequest.BodyPublishers.ofString(bodyStr)
                    : HttpRequest.BodyPublishers.noBody();

            switch (m) {
                case "POST" -> builder.POST(publisher);
                case "PUT" -> builder.PUT(publisher);
                case "PATCH" -> builder.method("PATCH", publisher);
                case "DELETE" -> builder.DELETE();
                case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                default -> builder.GET();
            }

            headers.forEach(builder::header);
            if (bodyStr != null && !headers.containsKey("Content-Type")) {
                builder.header("Content-Type", "application/json");
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            attemptStatus = response.statusCode();
            responseBody = response.body();
            // capture response headers
            Map<String, String> rh = new HashMap<>();
            response.headers().map().forEach((k, v) -> rh.put(k, String.join(", ", v)));
            responseHeaders = rh;

            // assertions
            assertionDetails = assertionEngine.evaluateHttp(attemptStatus, responseBody, ctx.config());
            boolean allPassed = assertionDetails.isEmpty() || assertionDetails.stream().allMatch(AssertionEngine.AssertionDetail::isPassed);

            // extract variables
            Map<String, Object> extracted = new HashMap<>();
            if (ctx.config().hasNonNull("extract") && ctx.config().get("extract").isArray()) {
                for (var ex : ctx.config().get("extract")) {
                    String name = ex.path("name").asText("");
                    String from = ex.path("from").asText("response_body");
                    String expr = ex.path("expression").asText("");
                    try {
                        Object val = JsonPath.using(jsonPathConfig).parse(responseBody == null ? "{}" : responseBody).read(expr);
                        String strVal = val == null ? "" : String.valueOf(val);
                        ctx.variableContext().put(name, strVal);
                        extracted.put(name, strVal);
                    } catch (Exception e) {
                        log.warn("Extract '{}' failed: {}", name, e.getMessage());
                    }
                }
            }

            StepResultStatus status = allPassed ? StepResultStatus.PASSED : StepResultStatus.FAILED;
            String assertionJson = new com.google.gson.Gson().toJson(assertionDetails);
            String extractedJson = extracted.isEmpty() ? null : new com.google.gson.Gson().toJson(extracted);

            return buildResult(ctx, status, attemptStatus, url, headers, bodyStr,
                    attemptStatus, responseHeaders, responseBody,
                    assertionJson, extractedJson, null, start, startedAt);

        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
            log.warn("HTTP step '{}' failed: {}", ctx.stepName(), errorMessage);
            return buildResult(ctx, StepResultStatus.ERROR, null, url, headers, bodyStr,
                    null, Map.of(), null, "[]", null, errorMessage, start, startedAt);
        }
    }

    private GradingStepResult buildResult(StepContext ctx, StepResultStatus status,
                                          Integer actualStatus, String url, Map<String, String> reqHeaders, String reqBody,
                                          Integer respStatus, Map<String, String> respHeaders, String respBody,
                                          String assertionJson, String extractedJson, String err,
                                          long startMs, OffsetDateTime startedAt) {
        // persist http_log for lecturer investigation (OUTBOUND, lecturer-only view)
        try {
            var httpLog = vn.edu.ptit.web_grading_system.executor_service.entities.HttpLog.builder()
                    .serviceName("executor-grading")
                    .direction(vn.edu.ptit.web_grading_system.executor_service.entities.HttpLogDirection.OUTBOUND)
                    .method(ctx.config().path("method").asText("GET"))
                    .url(url)
                    .port((Integer) ctx.variableContext().get("app_port"))
                    .requestHeaders(reqHeaders == null ? null : new com.google.gson.Gson().toJson(reqHeaders))
                    .requestBody(reqBody != null && reqBody.length() > 20000 ? reqBody.substring(0, 20000) : reqBody)
                    .statusCode(respStatus != null ? respStatus : actualStatus)
                    .responseHeaders(respHeaders == null ? null : new com.google.gson.Gson().toJson(respHeaders))
                    .responseBody(respBody != null && respBody.length() > 20000 ? respBody.substring(0, 20000) : respBody)
                    .durationMs((int) (System.currentTimeMillis() - startMs))
                    .build();
            httpLogService.save(httpLog);
        } catch (Exception ex) {
            log.warn("Failed to save http_log: {}", ex.getMessage());
        }

        return GradingStepResult.builder()
                .jobId(ctx.jobId())
                .planId(ctx.planId())
                .stepId(ctx.stepId())
                .stepOrder(ctx.stepOrder())
                .stepName(ctx.stepName())
                .stepType(type())
                .status(status)
                .actualStatusCode(actualStatus)
                .requestUrl(url)
                .requestHeaders(reqHeaders == null ? null : new com.google.gson.Gson().toJson(reqHeaders))
                .requestBody(reqBody)
                .responseStatusCode(respStatus)
                .responseHeaders(respHeaders == null ? null : new com.google.gson.Gson().toJson(respHeaders))
                .responseBody(respBody)
                .expectedStatusCode(ctx.config().hasNonNull("expected_status") ? ctx.config().get("expected_status").asInt() : null)
                .extractedVariables(extractedJson)
                .assertionResult(assertionJson)
                .errorMessage(err)
                .durationMs((int) (System.currentTimeMillis() - startMs))
                .startedAt(startedAt)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    public record StepContext(
            UUID jobId,
            UUID planId,
            UUID stepId,
            Integer stepOrder,
            String stepName,
            tools.jackson.databind.JsonNode config,
            VariableContext variableContext,
            Integer timeoutMs) {
    }
}
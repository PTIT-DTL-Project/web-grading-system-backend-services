package vn.edu.ptit.web_grading_system.executor_service.service.step;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
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
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpStepExecutor implements StepExecutor
{
    private final HttpLogService httpLogService;
    private final AssertionEngine assertionEngine;
    private final HttpClient httpClient;
    private final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
    private final Gson gson = new Gson();

    @Override
    public String type()
    {
        return Constant.HttpStep.HTTP_REQUEST;
    }

    @Override
    public GradingStepResult execute(StepContext ctx)
    {
        long start = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        int attemptStatus = 0;
        String responseBody = null;
        Map<String, String> responseHeaders = Map.of();
        String errorMessage = null;
        List<AssertionEngine.AssertionDetail> assertionDetails = List.of();

        String method = ctx.variableContext().substitute(Optional.ofNullable(ctx.config().path(Constant.HttpStep.METHOD).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.DEFAULT_METHOD));
        String rawPath = ctx.variableContext().substitute(Optional.ofNullable(ctx.config().path(Constant.HttpStep.PATH).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.PATH_DEFAULT));
        String url = "http://localhost:" + ctx.variableContext().get(Constant.VariableContext.APP_PORT) + rawPath;

        if (ctx.config().hasNonNull(Constant.HttpStep.QUERY_PARAMS) && ctx.config().get(Constant.HttpStep.QUERY_PARAMS).isObject())
        {
            StringBuilder queryPart = new StringBuilder();
            ctx.config().get(Constant.HttpStep.QUERY_PARAMS).properties().forEach(e ->
            {
                String v = ctx.variableContext().substitute(e.getValue().asString());
                if (queryPart.length() > 0)
                {
                    queryPart.append("&");
                }
                queryPart.append(e.getKey()).append("=").append(v);
            });
            if (queryPart.length() > 0)
            {
                url += (url.contains("?") ? "&" : "?") + queryPart;
            }
        }

        Map<String, String> headers = new HashMap<>();
        if (ctx.config().hasNonNull(Constant.HttpStep.HEADERS) && ctx.config().get(Constant.HttpStep.HEADERS).isObject())
        {
            ctx.config().get(Constant.HttpStep.HEADERS).properties().forEach(e ->
            {
                String v = ctx.variableContext().substitute(e.getValue().asString());
                headers.put(e.getKey(), v);
            });
        }

        String bodyStr = null;
        if (ctx.config().hasNonNull(Constant.HttpStep.BODY))
        {
            bodyStr = ctx.variableContext().substitute(ctx.config().get(Constant.HttpStep.BODY).toString());
        }

        int timeoutMs = ctx.config().path(Constant.HttpStep.TIMEOUT_MS).asInt(ctx.timeoutMs() != null ? ctx.timeoutMs() : 30000);

        try
        {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs));

            String m = method.toUpperCase();
            HttpRequest.BodyPublisher publisher = bodyStr != null
                    ? HttpRequest.BodyPublishers.ofString(bodyStr)
                    : HttpRequest.BodyPublishers.noBody();

            switch (m)
            {
                case Constant.HttpStep.HTTP_POST -> builder.POST(publisher);
                case Constant.HttpStep.HTTP_PUT -> builder.PUT(publisher);
                case Constant.HttpStep.HTTP_PATCH -> builder.method(Constant.HttpStep.HTTP_PATCH, publisher);
                case Constant.HttpStep.HTTP_DELETE -> builder.DELETE();
                case Constant.HttpStep.HTTP_HEAD -> builder.method(Constant.HttpStep.HTTP_HEAD, HttpRequest.BodyPublishers.noBody());
                default -> builder.GET();
            }

            headers.forEach(builder::header);
            if (bodyStr != null && !headers.containsKey(Constant.HttpStep.CONTENT_TYPE))
            {
                builder.header(Constant.HttpStep.CONTENT_TYPE, Constant.HttpStep.APPLICATION_JSON);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            attemptStatus = response.statusCode();
            responseBody = response.body();
            Map<String, String> rh = new HashMap<>();
            response.headers().map().forEach((k, v) -> rh.put(k, String.join(", ", v)));
            responseHeaders = rh;

            assertionDetails = assertionEngine.evaluateHttp(attemptStatus, responseBody, ctx.config());
            boolean allPassed = assertionDetails.isEmpty() || assertionDetails.stream().allMatch(AssertionEngine.AssertionDetail::isPassed);

            Map<String, Object> extracted = new HashMap<>();
            if (ctx.config().hasNonNull(Constant.HttpStep.EXTRACT) && ctx.config().get(Constant.HttpStep.EXTRACT).isArray())
            {
                for (var ex : ctx.config().get(Constant.HttpStep.EXTRACT))
                {
                    String name = ex.path(Constant.HttpStep.NAME).asString();
                    String from = Optional.ofNullable(ex.path(Constant.HttpStep.FROM).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.FROM_DEFAULT);
                    String expr = ex.path(Constant.HttpStep.EXPRESSION).asString();
                    try
                    {
                        Object val = JsonPath.using(jsonPathConfig).parse(responseBody == null ? "{}" : responseBody).read(expr);
                        String strVal = val == null ? "" : String.valueOf(val);
                        ctx.variableContext().put(name, strVal);
                        extracted.put(name, strVal);
                    }
                    catch (Exception e)
                    {
                        log.warn(Constant.Message.EXTRACT_FAILED, name, e.getMessage());
                    }
                }
            }

            StepResultStatus status = allPassed ? StepResultStatus.PASSED : StepResultStatus.FAILED;
            String assertionJson = gson.toJson(assertionDetails);
            String extractedJson = extracted.isEmpty() ? null : gson.toJson(extracted);

            return buildResult(ctx, status, attemptStatus, url, headers, bodyStr,
                    attemptStatus, responseHeaders, responseBody,
                    assertionJson, extractedJson, null, start, startedAt);
        }
        catch (Exception e)
        {
            errorMessage = safeMessage(e);
            log.warn(Constant.Message.HTTP_STEP_FAILED, ctx.stepName(), errorMessage);
            return buildResult(ctx, StepResultStatus.ERROR, null, url, headers, bodyStr,
                    null, Map.of(), null, "[]", null, errorMessage, start, startedAt);
        }
    }

    private GradingStepResult buildResult(StepContext ctx, StepResultStatus status,
                                          Integer actualStatus, String url, Map<String, String> reqHeaders, String reqBody,
                                          Integer respStatus, Map<String, String> respHeaders, String respBody,
                                          String assertionJson, String extractedJson, String err,
                                          long startMs, OffsetDateTime startedAt)
    {
        try
        {
            var httpLog = vn.edu.ptit.web_grading_system.executor_service.entities.HttpLog.builder()
                    .serviceName(Constant.HttpStep.SERVICE_NAME)
                    .direction(vn.edu.ptit.web_grading_system.executor_service.entities.HttpLogDirection.OUTBOUND)
                    .method(Optional.ofNullable(ctx.config().path(Constant.HttpStep.METHOD).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.DEFAULT_METHOD))
                    .url(url)
                    .port((Integer) ctx.variableContext().get(Constant.VariableContext.APP_PORT))
                    .requestHeaders(reqHeaders == null ? null : gson.toJson(reqHeaders))
                    .requestBody(reqBody != null && reqBody.length() > 20000 ? reqBody.substring(0, 20000) : reqBody)
                    .statusCode(respStatus != null ? respStatus : actualStatus)
                    .responseHeaders(respHeaders == null ? null : gson.toJson(respHeaders))
                    .responseBody(respBody != null && respBody.length() > 20000 ? respBody.substring(0, 20000) : respBody)
                    .durationMs((int) (System.currentTimeMillis() - startMs))
                    .build();
            httpLogService.save(httpLog);
        }
        catch (Exception ex)
        {
            log.warn(Constant.Message.HTTP_LOG_SAVE_FAILED, ex.getMessage());
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
                .requestHeaders(reqHeaders == null ? null : gson.toJson(reqHeaders))
                .requestBody(reqBody)
                .responseStatusCode(respStatus)
                .responseHeaders(respHeaders == null ? null : gson.toJson(respHeaders))
                .responseBody(respBody)
                .expectedStatusCode(ctx.config().hasNonNull(Constant.HttpStep.EXPECTED_STATUS) ? ctx.config().get(Constant.HttpStep.EXPECTED_STATUS).asInt() : null)
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
            Integer timeoutMs)
    {
    }

    private static String safeMessage(Exception e)
    {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}

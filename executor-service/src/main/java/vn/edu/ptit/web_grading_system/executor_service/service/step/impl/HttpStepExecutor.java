package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.entity.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entity.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.infra.HttpLogService;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepExecutor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpStepExecutor implements StepExecutor
{
    private final HttpLogService httpLogService;
    private final AssertionEngine assertionEngine;
    private final HttpClient httpClient;
    // Phase 4: Virtual thread executor — sendAsync().join() runs here so a slow/hanging
    // student app blocks a cheap virtual thread instead of a scarce platform thread.
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String type()
    {
        return Constant.HttpStep.HTTP_REQUEST;
    }

    @Override
    public GradingStepResult execute(StepContext ctx)
    {
        JsonNode config = ctx.getConfig();
        // Phase 3: data-driven loop (data[]) — run the step once per row in data[],
        // substituting ${index} and each row field into a fresh copy of the config.
        if (config.hasNonNull(Constant.HttpStep.DATA)
                && config.get(Constant.HttpStep.DATA).isArray()
                && config.get(Constant.HttpStep.DATA).size() > 0)
        {
            return executeDataLoop(ctx);
        }
        return executeSingle(ctx);
    }

    /**
     * Executes a single HTTP request (no {@code data[]} loop): substitute
     * variables, send the request, evaluate assertions, extract variables, and
     * build the step result. Each call logs its own {@link HttpLog} row.
     */
    private GradingStepResult executeSingle(StepContext ctx)
    {
        long start = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        int attemptStatus = 0;
        String responseBody = null;
        Map<String, String> responseHeaders = Map.of();
        String errorMessage = null;
        List<AssertionEngine.AssertionDetail> assertionDetails = List.of();

        String method = ctx.getVariableContext().substitute(Optional.ofNullable(ctx.getConfig().path(Constant.HttpStep.METHOD).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.DEFAULT_METHOD));
        String rawPath = ctx.getVariableContext().substitute(Optional.ofNullable(ctx.getConfig().path(Constant.HttpStep.PATH).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.PATH_DEFAULT));
        String url = "http://localhost:" + ctx.getVariableContext().get(Constant.VariableContext.APP_PORT) + rawPath;

        if (ctx.getConfig().hasNonNull(Constant.HttpStep.QUERY_PARAMS) && ctx.getConfig().get(Constant.HttpStep.QUERY_PARAMS).isObject())
        {
            StringBuilder queryPart = new StringBuilder();
            ctx.getConfig().get(Constant.HttpStep.QUERY_PARAMS).properties().forEach(e ->
            {
                String v = ctx.getVariableContext().substitute(e.getValue().asString());
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
        if (ctx.getConfig().hasNonNull(Constant.HttpStep.HEADERS) && ctx.getConfig().get(Constant.HttpStep.HEADERS).isObject())
        {
            ctx.getConfig().get(Constant.HttpStep.HEADERS).properties().forEach(e ->
            {
                String v = ctx.getVariableContext().substitute(e.getValue().asString());
                headers.put(e.getKey(), v);
            });
        }

        String bodyStr = null;
        if (ctx.getConfig().hasNonNull(Constant.HttpStep.BODY))
        {
            bodyStr = ctx.getVariableContext().substitute(ctx.getConfig().get(Constant.HttpStep.BODY).toString());
        }

        int timeoutMs = ctx.getConfig().path(Constant.HttpStep.TIMEOUT_MS).asInt(ctx.getTimeoutMs() != null ? ctx.getTimeoutMs() : 30000);

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

            // Phase 4: sendAsync() on a virtual thread — the caller's platform thread is
            // released while waiting for the HTTP response; a hanging student app only
            // parks a virtual thread, not a platform thread.
            HttpResponse<String> response = virtualThreadExecutor.submit(() ->
                    httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString()).join()
            ).get();
            attemptStatus = response.statusCode();
            responseBody = response.body();
            Map<String, String> rh = new HashMap<>();
            response.headers().map().forEach((k, v) -> rh.put(k, String.join(", ", v)));
            responseHeaders = rh;

            assertionDetails = assertionEngine.evaluateHttp(attemptStatus, responseBody, ctx.getConfig(), ctx.getVariableContext());
            boolean allPassed = assertionDetails.isEmpty() || assertionDetails.stream().allMatch(AssertionEngine.AssertionDetail::isPassed);

            Map<String, Object> extracted = new HashMap<>();
            if (ctx.getConfig().hasNonNull(Constant.HttpStep.EXTRACT) && ctx.getConfig().get(Constant.HttpStep.EXTRACT).isArray())
            {
                for (var ex : ctx.getConfig().get(Constant.HttpStep.EXTRACT))
                {
                    String name = ex.path(Constant.HttpStep.NAME).asString();
                    String from = Optional.ofNullable(ex.path(Constant.HttpStep.FROM).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.FROM_DEFAULT);
                    String expr = ex.path(Constant.HttpStep.EXPRESSION).asString();
                    String asType = Optional.ofNullable(ex.path(Constant.HttpStep.AS).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.AS_SCALAR);
                    String fallback = ex.hasNonNull(Constant.HttpStep.FALLBACK) ? ex.path(Constant.HttpStep.FALLBACK).asString() : null;
                    try
                    {
                        Object val = JsonPath.using(jsonPathConfig).parse(responseBody == null ? "{}" : responseBody).read(expr);
                        String strVal;
                        if (val == null)
                        {
                            // Missing field: use fallback if configured, else empty string.
                            strVal = fallback != null ? fallback : "";
                        }
                        else if (Constant.HttpStep.AS_JSON.equals(asType))
                        {
                            strVal = gson.toJson(val);
                        }
                        else
                        {
                            strVal = String.valueOf(val);
                        }
                        ctx.getVariableContext().put(name, strVal);
                        extracted.put(name, strVal);
                    }
                    catch (Exception e)
                    {
                        // JsonPath expression did not match: use fallback if configured,
                        // otherwise empty string. Do NOT crash the whole grading job.
                        String strVal = fallback != null ? fallback : "";
                        ctx.getVariableContext().put(name, strVal);
                        extracted.put(name, strVal);
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
            log.warn(Constant.Message.HTTP_STEP_FAILED, ctx.getStepName(), errorMessage);
            return buildResult(ctx, StepResultStatus.ERROR, null, url, headers, bodyStr,
                    null, Map.of(), null, "[]", null, errorMessage, start, startedAt);
        }
    }

    /**
     * Phase 3: data-driven loop. Runs the step once per entry in {@code data[]}.
     * Each iteration substitutes {@code ${index}} and the row's fields into a
     * fresh copy of the config, then reuses the shared {@link VariableContext}
     * (so {@code ${accessToken}}, {@code ${$UUID}} and previously extracted vars
     * still resolve). Extracted variables such as {@code bookId_${index}} are
     * stored into the shared context and survive the loop.
     * <p>
     * The step result is an aggregate: PASSED only if every iteration passed,
     * FAILED if any iteration failed, ERROR if any iteration errored. Assertion
     * details and extracted variables from all iterations are merged into the
     * single step result row; HTTP metadata comes from the last iteration.
     * <p>
     * Loop Safety Governor: a for-each over more than
     * {@link Constant.Loop#FOREACH_TRUNCATE} rows is truncated at runtime so a
     * lecturer cannot accidentally start thousands of requests in one step.
     */
    private GradingStepResult executeDataLoop(StepContext ctx)
    {
        JsonNode config = ctx.getConfig();
        JsonNode dataNode = config.get(Constant.HttpStep.DATA);
        List<JsonNode> entries = new ArrayList<>();
        if (dataNode.isArray())
        {
            for (JsonNode e : (ArrayNode) dataNode)
            {
                entries.add(e);
            }
        }

        int limit = Math.min(entries.size(), Constant.Loop.FOREACH_TRUNCATE);
        if (limit < entries.size())
        {
            log.warn("data[] truncated from {} to {} entries (loop safety governor)",
                    entries.size(), Constant.Loop.FOREACH_TRUNCATE);
        }

        long start = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        ArrayNode allDetailsArray = objectMapper.createArrayNode();
        ObjectNode allExtractedObj = objectMapper.createObjectNode();
        StepResultStatus overall = StepResultStatus.PASSED;
        GradingStepResult lastResult = null;

        for (int i = 0; i < limit; i++)
        {
            JsonNode entry = entries.get(i);
            Map<String, String> replacements = new LinkedHashMap<>();
            replacements.put("index", String.valueOf(i));
            if (entry.isObject())
            {
                entry.properties().forEach(e -> {
                    replacements.put(e.getKey(), e.getValue().asString());
                });
            }
            JsonNode iterConfig = substituteDataVars(config, replacements);
            StepContext iterCtx = ctx.withConfig(iterConfig);
            GradingStepResult result = executeSingle(iterCtx);
            lastResult = result;

            if (result.getAssertionResult() != null)
            {
                try
                {
                    JsonNode details = objectMapper.readTree(result.getAssertionResult());
                    if (details.isArray())
                    {
                        for (JsonNode d : details)
                        {
                            allDetailsArray.add(d);
                        }
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse assertion result for data[] iteration {}: {}", i, e.getMessage());
                }
            }
            if (result.getExtractedVariables() != null)
            {
                try
                {
                    JsonNode extracted = objectMapper.readTree(result.getExtractedVariables());
                    if (extracted.isObject())
                    {
                        extracted.properties().forEach(e -> {
                            allExtractedObj.set(e.getKey(), e.getValue());
                        });
                    }
                }
                catch (Exception e)
                {
                    log.warn("Failed to parse extracted variables for data[] iteration {}: {}", i, e.getMessage());
                }
            }

            StepResultStatus status = result.getStatus();
            if (status == StepResultStatus.ERROR)
            {
                overall = StepResultStatus.ERROR;
            }
            else if (status == StepResultStatus.FAILED && overall != StepResultStatus.ERROR)
            {
                overall = StepResultStatus.FAILED;
            }
        }

        String assertionJson = allDetailsArray.isEmpty() ? "[]" : allDetailsArray.toString();
        String extractedJson = allExtractedObj.isEmpty() ? null : allExtractedObj.toString();

        GradingStepResult base = lastResult;
        // Aggregate into one step result row. HTTP metadata is taken from the last
        // iteration; assertions and extracted variables are merged across all.
        String stackTrace = base.getErrorMessage() != null ? extractStackTrace(base.getErrorMessage()) : null;
        return GradingStepResult.builder()
                .jobId(ctx.getJobId())
                .planId(ctx.getPlanId())
                .stepId(ctx.getStepId())
                .stepOrder(ctx.getStepOrder())
                .stepName(ctx.getStepName())
                .stepType(type())
                .status(overall)
                .actualStatusCode(base.getActualStatusCode())
                .requestUrl(base.getRequestUrl())
                .requestHeaders(base.getRequestHeaders())
                .requestBody(base.getRequestBody())
                .responseStatusCode(base.getResponseStatusCode())
                .responseHeaders(base.getResponseHeaders())
                .responseBody(base.getResponseBody())
                .expectedStatusCode(ctx.getConfig().hasNonNull(Constant.HttpStep.EXPECTED_STATUS)
                        ? ctx.getConfig().get(Constant.HttpStep.EXPECTED_STATUS).asInt()
                        : null)
                .extractedVariables(extractedJson)
                .assertionResult(assertionJson)
                .errorMessage(base.getErrorMessage())
                .stackTrace(stackTrace)
                .durationMs((int) (System.currentTimeMillis() - start))
                .startedAt(startedAt)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Returns a deep copy of {@code node} where every occurrence of
     * {@code ${key}} (for each entry in {@code replacements}) is replaced with
     * its value. Works on textual nodes only; numbers/booleans/null pass through.
     * This lets a data row such as {@code {"title":"Sách A","year":"${year}"}}
     * fill placeholders in both string and (after substitution) numeric positions.
     */
    private JsonNode substituteDataVars(JsonNode node, Map<String, String> replacements)
    {
        if (node.isObject())
        {
            ObjectNode copy = objectMapper.createObjectNode();
            node.properties().forEach(e -> {
                copy.set(e.getKey(), substituteDataVars(e.getValue(), replacements));
            });
            return copy;
        }
        if (node.isArray())
        {
            ArrayNode copy = objectMapper.createArrayNode();
            for (JsonNode el : node)
            {
                copy.add(substituteDataVars(el, replacements));
            }
            return copy;
        }
        if (node.isTextual())
        {
            String s = node.asText();
            for (Map.Entry<String, String> e : replacements.entrySet())
            {
                s = s.replace("${" + e.getKey() + "}", e.getValue());
            }
            return objectMapper.valueToTree(s);
        }
        return node;
    }

    /**
     * http_log bodies share the ...(truncated N/M) marker contract (phase A3).
     * Note: operator-side log rows are NOT redacted (unlike student evidence).
     */
    private static String cutForLog(String body)
    {
        if (body == null || body.length() <= 20000)
        {
            return body;
        }
        return body.substring(0, 20000) + "...(truncated 20000/" + body.length() + ")";
    }

    private GradingStepResult buildResult(StepContext ctx, StepResultStatus status,                                          Integer actualStatus, String url, Map<String, String> reqHeaders, String reqBody,
                                          Integer respStatus, Map<String, String> respHeaders, String respBody,
                                          String assertionJson, String extractedJson, String err,
                                          long startMs, OffsetDateTime startedAt)
    {
        try
        {
            var httpLog = vn.edu.ptit.web_grading_system.executor_service.entity.HttpLog.builder()
                    .serviceName(Constant.HttpStep.SERVICE_NAME)
                    .direction(vn.edu.ptit.web_grading_system.executor_service.entity.HttpLogDirection.OUTBOUND)
                    .method(Optional.ofNullable(ctx.getConfig().path(Constant.HttpStep.METHOD).asString()).filter(s -> !s.isEmpty()).orElse(Constant.HttpStep.DEFAULT_METHOD))
                    .url(url)
                    .port((Integer) ctx.getVariableContext().get(Constant.VariableContext.APP_PORT))
                    .requestHeaders(reqHeaders == null ? null : gson.toJson(reqHeaders))
                    // Review: 2026-10-11, phase A3 — same ...(truncated N/M)
                    // marker contract as stepItem/result-service: a cut body
                    // must never read as the full body.
                    .requestBody(cutForLog(reqBody))
                    .statusCode(respStatus != null ? respStatus : actualStatus)
                    .responseHeaders(respHeaders == null ? null : gson.toJson(respHeaders))
                    .responseBody(cutForLog(respBody))
                    .durationMs((int) (System.currentTimeMillis() - startMs))
                    .build();
            httpLogService.save(httpLog);
        }
        catch (Exception ex)
        {
            log.warn(Constant.Message.HTTP_LOG_SAVE_FAILED, ex.getMessage());
        }

        String stackTrace = (respStatus != null && respStatus >= 500) || (actualStatus != null && actualStatus >= 500)
                ? extractStackTrace(respBody) : null;
        if (stackTrace == null && err != null)
        {
            stackTrace = extractStackTrace(err);
        }

        return GradingStepResult.builder()
                .jobId(ctx.getJobId())
                .planId(ctx.getPlanId())
                .stepId(ctx.getStepId())
                .stepOrder(ctx.getStepOrder())
                .stepName(ctx.getStepName())
                .stepType(type())
                .status(status)
                .actualStatusCode(actualStatus)
                .requestUrl(url)
                .requestHeaders(reqHeaders == null ? null : gson.toJson(reqHeaders))
                .requestBody(reqBody)
                .responseStatusCode(respStatus)
                .responseHeaders(respHeaders == null ? null : gson.toJson(respHeaders))
                .responseBody(respBody)
                .expectedStatusCode(ctx.getConfig().hasNonNull(Constant.HttpStep.EXPECTED_STATUS) ? ctx.getConfig().get(Constant.HttpStep.EXPECTED_STATUS).asInt() : null)
                .extractedVariables(extractedJson)
                .assertionResult(assertionJson)
                .errorMessage(err)
                .stackTrace(stackTrace)
                .durationMs((int) (System.currentTimeMillis() - startMs))
                .startedAt(startedAt)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    public static List<String> extractSqlQueries(String logContent)
    {
        if (logContent == null || logContent.isBlank())
        {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?i)(statement:\\s*|Hibernate:\\s*)(SELECT|INSERT|UPDATE|DELETE)\\b.*", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(logContent);
        while (matcher.find())
        {
            list.add(matcher.group().trim());
        }
        return list;
    }

    public static String extractStackTrace(String responseBody)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return null;
        }
        Pattern pattern = Pattern.compile("(?s)(?:at\\s+[\\w\\.$_]+\\(.*?:\\d+\\)|Exception:.*)");
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find())
        {
            return responseBody.length() <= 10000 ? responseBody : responseBody.substring(0, 10000);
        }
        return null;
    }

    // Phase 4: sendAsync().join() on a virtual thread wraps checked exceptions in
    // ExecutionException/CompletionException. Unwrap to surface the root cause message.
    private static String safeMessage(Throwable t)
    {
        Throwable root = t;
        while (root instanceof java.util.concurrent.ExecutionException
                || root instanceof java.util.concurrent.CompletionException)
        {
            root = root.getCause();
            if (root == null)
            {
                return "Unknown error";
            }
        }
        String msg = root.getMessage();
        return msg != null ? msg : root.toString();
    }
}

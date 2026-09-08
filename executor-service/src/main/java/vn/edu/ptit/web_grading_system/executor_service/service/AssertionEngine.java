package vn.edu.ptit.web_grading_system.executor_service.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.executor_service.util.GsonStructureComparator;

import java.util.ArrayList;
import java.util.List;

public class AssertionEngine {

    private final Gson gson = new Gson();
    private final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssertionDetail {
        private String kind;
        private Object expected;
        private Object actual;
        private boolean passed;
        private String message;
    }

    /**
     * Evaluate HTTP assertions from config.
     * config is the step's config JsonNode (parsed), actualBody is response body string.
     */
    public List<AssertionDetail> evaluateHttp(int actualStatus, String actualBody, tools.jackson.databind.JsonNode config) {
        List<AssertionDetail> results = new ArrayList<>();
        if (config == null) {
            return results;
        }

        // expected_status check
        if (config.hasNonNull("expected_status")) {
            int expected = config.get("expected_status").asInt();
            boolean passed = actualStatus == expected;
            results.add(AssertionDetail.builder()
                    .kind("status")
                    .expected(expected)
                    .actual(actualStatus)
                    .passed(passed)
                    .message(passed ? "status matched" : "Expected status " + expected + " but got " + actualStatus)
                    .build());
        }

        if (!config.hasNonNull("assertions") || !config.get("assertions").isArray()) {
            return results;
        }

        for (tools.jackson.databind.JsonNode assertion : config.get("assertions")) {
            String kind = assertion.path("kind").asText("");
            AssertionDetail detail = switch (kind) {
                case "status" -> checkStatus(assertion, actualStatus);
                case "contains" -> checkContains(assertion, actualBody);
                case "json_path" -> checkJsonPath(assertion, actualBody);
                case "body_equals" -> checkBodyEquals(assertion, actualBody);
                case "body_structure" -> checkBodyStructure(assertion, actualBody);
                default -> AssertionDetail.builder()
                        .kind(kind).passed(false)
                        .message("Unknown assertion kind: " + kind).build();
            };
            results.add(detail);
        }
        return results;
    }

    private AssertionDetail checkStatus(tools.jackson.databind.JsonNode assertion, int actualStatus) {
        int expected = assertion.path("equals").asInt();
        boolean passed = actualStatus == expected;
        return AssertionDetail.builder()
                .kind("status").expected(expected).actual(actualStatus).passed(passed)
                .message(passed ? "status matched" : "Expected status " + expected + " but got " + actualStatus)
                .build();
    }

    private AssertionDetail checkContains(tools.jackson.databind.JsonNode assertion, String actualBody) {
        String text = assertion.path("text").asText("");
        boolean passed = actualBody != null && actualBody.contains(text);
        return AssertionDetail.builder()
                .kind("contains").expected(text).actual(actualBody).passed(passed)
                .message(passed ? "body contains '" + text + "'" : "Body does not contain '" + text + "'")
                .build();
    }

    private AssertionDetail checkJsonPath(tools.jackson.databind.JsonNode assertion, String actualBody) {
        String path = assertion.path("path").asText("");
        boolean shouldExist = !assertion.has("exists") || assertion.get("exists").asBoolean(true);
        try {
            Object result = JsonPath.using(jsonPathConfig).parse(actualBody == null ? "{}" : actualBody).read(path);
            boolean exists = result != null && (!(result instanceof List) || !((List<?>) result).isEmpty());
            boolean passed = exists == shouldExist;
            return AssertionDetail.builder()
                    .kind("json_path").expected(path).actual(result).passed(passed)
                    .message(passed ? "json_path '" + path + "' existence matched"
                            : "JSONPath '" + path + "' expected exists=" + shouldExist + " but was " + exists)
                    .build();
        } catch (Exception e) {
            boolean passed = !shouldExist;
            return AssertionDetail.builder()
                    .kind("json_path").expected(path).actual(null).passed(passed)
                    .message(passed ? "json_path correctly not found" : "JSONPath error: " + e.getMessage())
                    .build();
        }
    }

    private AssertionDetail checkBodyEquals(tools.jackson.databind.JsonNode assertion, String actualBody) {
        String expectedJson = assertion.path("json").toString();
        try {
            JsonElement actualEl = gson.fromJson(actualBody == null ? "null" : actualBody, JsonElement.class);
            JsonElement expectedEl = gson.fromJson(expectedJson, JsonElement.class);
            boolean passed = expectedEl.equals(actualEl);
            return AssertionDetail.builder()
                    .kind("body_equals").expected(expectedEl).actual(actualEl).passed(passed)
                    .message(passed ? "body equals matched" : "Body not equal. Expected: " + expectedJson)
                    .build();
        } catch (Exception e) {
            return AssertionDetail.builder()
                    .kind("body_equals").passed(false)
                    .message("body_equals parse error: " + e.getMessage()).build();
        }
    }

    private AssertionDetail checkBodyStructure(tools.jackson.databind.JsonNode assertion, String actualBody) {
        String expectedJson = assertion.path("json").toString();
        try {
            JsonElement actualEl = gson.fromJson(actualBody == null ? "null" : actualBody, JsonElement.class);
            JsonElement expectedEl = gson.fromJson(expectedJson, JsonElement.class);
            boolean passed = GsonStructureComparator.sameStructure(expectedEl, actualEl);
            return AssertionDetail.builder()
                    .kind("body_structure").expected(expectedEl).actual(actualEl).passed(passed)
                    .message(passed ? "body structure matched" : "Body structure mismatch. Expected keys: " + expectedJson)
                    .build();
        } catch (Exception e) {
            return AssertionDetail.builder()
                    .kind("body_structure").passed(false)
                    .message("body_structure parse error: " + e.getMessage()).build();
        }
    }
}
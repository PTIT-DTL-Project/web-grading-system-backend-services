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
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;
import vn.edu.ptit.web_grading_system.executor_service.util.GsonStructureComparator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class AssertionEngine
{
    private final Gson gson = new Gson();
    private final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssertionDetail
    {
        private String kind;
        private Object expected;
        private Object actual;
        private boolean passed;
        private String message;
    }

    public List<AssertionDetail> evaluateHttp(int actualStatus, String actualBody,
            tools.jackson.databind.JsonNode config, VariableContext vars)
    {
        List<AssertionDetail> results = new ArrayList<>();
        if (config == null)
        {
            return results;
        }

        if (config.hasNonNull(Constant.HttpStep.EXPECTED_STATUS))
        {
            int expected = config.get(Constant.HttpStep.EXPECTED_STATUS).asInt();
            boolean passed = actualStatus == expected;
            results.add(AssertionDetail.builder()
                    .kind(Constant.Assertion.STATUS)
                    .expected(expected)
                    .actual(actualStatus)
                    .passed(passed)
                    .message(passed ? Constant.Message.STATUS_MATCHED : "Expected status " + expected + " but got " + actualStatus)
                    .build());
        }

        if (!config.hasNonNull(Constant.Assertion.ASSERTIONS) || !config.get(Constant.Assertion.ASSERTIONS).isArray())
        {
            return results;
        }

        for (tools.jackson.databind.JsonNode assertion : config.get(Constant.Assertion.ASSERTIONS))
        {
            String kind = assertion.path(Constant.Assertion.KIND).asString();
            AssertionDetail detail = switch (kind)
            {
                case Constant.Assertion.STATUS -> checkStatus(assertion, actualStatus);
                case Constant.Assertion.CONTAINS -> checkContains(assertion, actualBody);
                case Constant.Assertion.JSON_PATH -> checkJsonPath(assertion, actualBody);
                case Constant.Assertion.BODY_EQUALS -> checkBodyEquals(assertion, actualBody);
                case Constant.Assertion.BODY_STRUCTURE -> checkBodyStructure(assertion, actualBody);
                case Constant.Assertion.FIELD_EQUALS -> checkFieldEquals(assertion, actualBody, vars);
                default -> AssertionDetail.builder()
                        .kind(kind).passed(false)
                        .message(Constant.Message.UNKNOWN_ASSERTION_KIND_PREFIX + kind).build();
            };
            results.add(detail);
        }
        return results;
    }

    private AssertionDetail checkStatus(tools.jackson.databind.JsonNode assertion, int actualStatus)
    {
        int expected = assertion.path(Constant.Assertion.EQUALS).asInt();
        boolean passed = actualStatus == expected;
        return AssertionDetail.builder()
                .kind(Constant.Assertion.STATUS).expected(expected).actual(actualStatus).passed(passed)
                .message(passed ? Constant.Message.STATUS_MATCHED : "Expected status " + expected + " but got " + actualStatus)
                .build();
    }

    private AssertionDetail checkContains(tools.jackson.databind.JsonNode assertion, String actualBody)
    {
        String text = assertion.path(Constant.Assertion.TEXT).asString();
        boolean passed = actualBody != null && actualBody.contains(text);
        return AssertionDetail.builder()
                .kind(Constant.Assertion.CONTAINS).expected(text).actual(actualBody).passed(passed)
                .message(passed ? Constant.Message.BODY_CONTAINS_PREFIX + text + Constant.Message.BODY_CONTAINS_SUFFIX : Constant.Message.BODY_DOES_NOT_CONTAIN_PREFIX + text + Constant.Message.BODY_DOES_NOT_CONTAIN_SUFFIX)
                .build();
    }

    private AssertionDetail checkJsonPath(tools.jackson.databind.JsonNode assertion, String actualBody)
    {
        String path = assertion.path(Constant.Assertion.PATH).asString();
        boolean shouldExist = !assertion.has(Constant.Assertion.EXISTS) || assertion.get(Constant.Assertion.EXISTS).asBoolean(true);
        try
        {
            Object result = JsonPath.using(jsonPathConfig).parse(actualBody == null ? "{}" : actualBody).read(path);
            boolean exists = result != null && (!(result instanceof List) || !((List<?>) result).isEmpty());
            boolean passed = exists == shouldExist;
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.JSON_PATH).expected(path).actual(result).passed(passed)
                    .message(passed ? Constant.Message.JSON_PATH_EXISTENCE_MATCHED_PREFIX + path + Constant.Message.JSON_PATH_EXISTENCE_MATCHED_MIDDLE : Constant.Message.JSON_PATH_EXPECTED_EXISTS_PREFIX + path + Constant.Message.JSON_PATH_EXPECTED_EXISTS_MIDDLE + shouldExist + " but was " + exists)
                    .build();
        }
        catch (Exception e)
        {
            boolean passed = !shouldExist;
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.JSON_PATH).expected(path).actual(null).passed(passed)
                    .message(passed ? Constant.Message.JSON_PATH_CORRECTLY_NOT_FOUND : Constant.Message.JSON_PATH_ERROR_PREFIX + e.getMessage())
                    .build();
        }
    }

    private AssertionDetail checkBodyEquals(tools.jackson.databind.JsonNode assertion, String actualBody)
    {
        String expectedJson = assertion.path(Constant.Assertion.JSON).toString();
        try
        {
            JsonElement actualEl = gson.fromJson(actualBody == null ? Constant.Assertion.NULL_JSON : actualBody, JsonElement.class);
            JsonElement expectedEl = gson.fromJson(expectedJson, JsonElement.class);
            boolean passed = expectedEl.equals(actualEl);
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.BODY_EQUALS).expected(expectedEl).actual(actualEl).passed(passed)
                    .message(passed ? Constant.Message.BODY_EQUALS_MATCHED : Constant.Message.BODY_NOT_EQUAL_PREFIX + expectedJson)
                    .build();
        }
        catch (Exception e)
        {
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.BODY_EQUALS).passed(false)
                    .message(Constant.Message.BODY_EQUALS_PARSE_ERROR_PREFIX + e.getMessage()).build();
        }
    }

    private AssertionDetail checkBodyStructure(tools.jackson.databind.JsonNode assertion, String actualBody)
    {
        String expectedJson = assertion.path(Constant.Assertion.JSON).toString();
        try
        {
            JsonElement actualEl = gson.fromJson(actualBody == null ? Constant.Assertion.NULL_JSON : actualBody, JsonElement.class);
            JsonElement expectedEl = gson.fromJson(expectedJson, JsonElement.class);
            boolean passed = GsonStructureComparator.sameStructure(expectedEl, actualEl);
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.BODY_STRUCTURE).expected(expectedEl).actual(actualEl).passed(passed)
                    .message(passed ? Constant.Message.BODY_STRUCTURE_MATCHED : Constant.Message.BODY_STRUCTURE_MISMATCH_PREFIX + expectedJson)
                    .build();
        }
        catch (Exception e)
        {
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.BODY_STRUCTURE).passed(false)
                    .message(Constant.Message.BODY_STRUCTURE_PARSE_ERROR_PREFIX + e.getMessage()).build();
        }
    }

    private AssertionDetail checkFieldEquals(tools.jackson.databind.JsonNode assertion,
            String actualBody, VariableContext vars)
    {
        String path = assertion.path(Constant.Assertion.PATH).asString();
        String expectedRaw = assertion.path(Constant.Assertion.EQUALS).asString();
        String expected = vars != null ? vars.substitute(expectedRaw) : expectedRaw;
        try
        {
            Object actual = JsonPath.using(jsonPathConfig)
                    .parse(actualBody == null ? "{}" : actualBody)
                    .read(path);
            /*
             * Compare field value against expected.
             * - null actual (missing field) → always FAIL (never match "null")
             * - Number instances → compare via BigDecimal (1.0 == 1, exact)
             * - non-Number types → exact string equality
             * Review: 2026-09-20, Pullfrog PR #16.
             */
            boolean passed = fieldEquals(expected, actual);
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.FIELD_EQUALS).expected(expected).actual(actual)
                    .passed(passed)
                    .message(passed ? "Field equals matched"
                            : "Field not equal. Expected: " + expected + " but was " + actual)
                    .build();
        }
        catch (Exception e)
        {
            return AssertionDetail.builder()
                    .kind(Constant.Assertion.FIELD_EQUALS).expected(expected).actual(null)
                    .passed(false)
                    .message(Constant.Message.JSON_PATH_ERROR_PREFIX + e.getMessage())
                    .build();
        }
    }

    /*
     * Compare expected string against actual field value.
     * - null actual (missing field or explicit JSON null) → always FAIL
     * - Number instances → compare numerically via BigDecimal (1.0 == 1, no precision loss)
     * - String or other types → exact string equality ("00123" ≠ "123")
     * Review: 2026-09-20, Pullfrog PR #16.
     */
    private boolean fieldEquals(String expected, Object actual)
    {
        if (actual == null)
        {
            return false;
        }
        if (actual instanceof Number actualNum)
        {
            try
            {
                BigDecimal expectedNum = new BigDecimal(expected);
                return expectedNum.compareTo(new BigDecimal(actualNum.toString())) == 0;
            }
            catch (NumberFormatException e)
            {
                return false;
            }
        }
        return expected.equals(String.valueOf(actual));
    }

    private static String safeMessage(Exception e)
    {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}

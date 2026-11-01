package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Function;

final class ReadToolCallGuard {
    static class RepeatedReadCallException extends IllegalStateException {
        RepeatedReadCallException(String toolName) {
            super("Repeated identical read call: " + toolName);
        }
    }

    private static final class CachedRead {
        private final String result;

        private CachedRead(String result) { this.result = result; }
    }

    private final ObjectMapper mapper;
    private final Map<String, CachedRead> previousReads = new HashMap<>();

    ReadToolCallGuard(ObjectMapper mapper) { this.mapper = mapper; }

    synchronized String call(String toolName, String input, Function<String, String> execute) {
        JsonNode arguments = parse(input);
        if (arguments == null || !arguments.isObject()) return execute.apply(input);
        String signature = toolName + ":" + canonical(arguments);
        CachedRead cached = previousReads.get(signature);
        if (cached != null) {
            return cached.result;
        }

        String result = execute.apply(input);
        JsonNode response = parse(result);
        JsonNode requestedPage = arguments.path("page");
        if (requestedPage.isIntegralNumber() && requestedPage.asInt() > 0
                && response != null && response.isObject()
                && response.path("items").isArray() && response.path("items").isEmpty()
                && response.path("totalElements").asLong(0) > 0
                && response.path("totalPages").asInt(0) > 0
                && requestedPage.asInt() >= response.path("totalPages").asInt()
                && response.path("page").asInt(-1) == requestedPage.asInt()) {
            ObjectNode firstPageArgs = ((ObjectNode) arguments).deepCopy();
            firstPageArgs.put("page", 0);
            String firstPage = execute.apply(firstPageArgs.toString());
            JsonNode firstResponse = parse(firstPage);
            if (firstResponse instanceof ObjectNode firstObject) {
                ObjectNode corrected = firstObject.deepCopy();
                corrected.put("requestedPage", requestedPage.asInt());
                corrected.put("servedPage", 0);
                result = corrected.toString();
            } else {
                result = firstPage;
            }
        }
        JsonNode delivered = parse(result);
        String search = arguments.path("search").asText("").strip();
        if (!search.isEmpty() && delivered instanceof ObjectNode object
                && object.path("items").isArray()) {
            ObjectNode enriched = object.deepCopy();
            ArrayNode exact = mapper.createArrayNode();
            String wanted = normalizedName(search);
            for (JsonNode item : object.path("items")) {
                String name = item.path("title").asText(item.path("name").asText(""));
                if (normalizedName(name).equals(wanted)) exact.add(item);
            }
            enriched.set("exactMatches", exact);
            enriched.put("exactMatchCount", exact.size());
            result = enriched.toString();
        }
        if (delivered != null && !delivered.has("error")
                && (!delivered.path("success").isBoolean() || delivered.path("success").asBoolean())) {
            previousReads.put(signature, new CachedRead(result));
        }
        return result;
    }

    private String normalizedName(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, String> ordered = new TreeMap<>();
            node.fields().forEachRemaining(field -> ordered.put(field.getKey(), canonical(field.getValue())));
            StringJoiner joined = new StringJoiner(",", "{", "}");
            ordered.forEach((key, value) -> joined.add(mapper.valueToTree(key).toString() + ":" + value));
            return joined.toString();
        }
        if (node.isArray()) {
            StringJoiner joined = new StringJoiner(",", "[", "]");
            node.forEach(value -> joined.add(canonical(value)));
            return joined.toString();
        }
        return node.toString();
    }

    private JsonNode parse(String value) {
        try {
            return value == null ? null : mapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}

package vn.edu.ptit.web_grading_system.executor_service.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class GsonStructureComparator {

    private GsonStructureComparator() {
    }

    /**
     * Deep compare structure only: keys must match (unordered), array sizes must match,
     * primitive types must match. Values are ignored.
     */
    public static boolean sameStructure(JsonElement expected, JsonElement actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isJsonObject() && actual.isJsonObject()) {
            JsonObject expObj = expected.getAsJsonObject();
            JsonObject actObj = actual.getAsJsonObject();
            if (!expObj.keySet().equals(actObj.keySet())) {
                return false;
            }
            for (String key : expObj.keySet()) {
                if (!sameStructure(expObj.get(key), actObj.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isJsonArray() && actual.isJsonArray()) {
            JsonArray expArr = expected.getAsJsonArray();
            JsonArray actArr = actual.getAsJsonArray();
            if (expArr.size() != actArr.size()) {
                return false;
            }
            for (int i = 0; i < expArr.size(); i++) {
                if (!sameStructure(expArr.get(i), actArr.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isJsonPrimitive() && actual.isJsonPrimitive()) {
            return samePrimitiveType(expected.getAsJsonPrimitive(), actual.getAsJsonPrimitive());
        }
        if (expected.isJsonNull() && actual.isJsonNull()) {
            return true;
        }
        return false;
    }

    private static boolean samePrimitiveType(JsonPrimitive a, JsonPrimitive b) {
        if (a.isString() && b.isString()) return true;
        if (a.isNumber() && b.isNumber()) return true;
        if (a.isBoolean() && b.isBoolean()) return true;
        return false;
    }
}
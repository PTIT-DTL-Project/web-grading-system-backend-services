package vn.edu.ptit.web_grading_system.executor_service.util;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonStructureComparatorTest {

    private static com.google.gson.JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    @Test
    void unorderedKeysWithDifferentValues_match() {
        assertTrue(GsonStructureComparator.sameStructure(
                json("{\"id\":\"\",\"title\":\"\",\"year\":0}"),
                json("{\"year\":1941,\"title\":\"De Men\",\"id\":\"abc\"}")));
    }

    @Test
    void missingKey_mismatch() {
        assertFalse(GsonStructureComparator.sameStructure(
                json("{\"id\":\"\",\"title\":\"\"}"), json("{\"id\":\"\"}")));
    }

    @Test
    void extraKeyOnActual_mismatch() {
        assertFalse(GsonStructureComparator.sameStructure(
                json("{\"id\":\"\"}"), json("{\"id\":\"\",\"extra\":1}")));
    }

    @Test
    void nestedObjects_recurse() {
        assertTrue(GsonStructureComparator.sameStructure(
                json("{\"data\":{\"books\":[{\"id\":\"\",\"price\":0.0}]}}"),
                json("{\"data\":{\"books\":[{\"id\":\"x\",\"price\":9.9}]}}")));
        assertFalse(GsonStructureComparator.sameStructure(
                json("{\"data\":{\"books\":[{\"id\":\"\"}]}}"),
                json("{\"data\":{\"books\":[{\"id\":\"x\",\"extra\":true}]}}")));
    }

    @Test
    void primitiveTypeMismatch_fails() {
        assertFalse(GsonStructureComparator.sameStructure(json("{\"id\":\"\"}"), json("{\"id\":1}")));
        assertTrue(GsonStructureComparator.sameStructure(json("{\"price\":0}"), json("{\"price\":9.99}")));
    }

    @Test
    void arraySizeMismatch_fails() {
        assertFalse(GsonStructureComparator.sameStructure(json("[1,2,3]"), json("[1,2]")));
    }
}
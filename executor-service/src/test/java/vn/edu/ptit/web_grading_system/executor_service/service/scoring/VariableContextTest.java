package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableContextTest {

    @Test
    void substitute_replacesMultipleVariables() {
        VariableContext ctx = new VariableContext();
        ctx.put("bookId", "abc-123");
        ctx.put("page", 10);

        assertEquals("GET /api/v1/books/abc-123?page=10",
                ctx.substitute("GET /api/v1/books/${bookId}?page=${page}"));
    }

    @Test
    void missingVariable_replacedWithEmptyString() {
        VariableContext ctx = new VariableContext();
        assertEquals("/api/v1/books/", ctx.substitute("/api/v1/books/${nope}"));
    }

    @Test
    void nullTemplate_returnsNull() {
        VariableContext ctx = new VariableContext();
        assertNull(ctx.substitute(null));
    }

    @Test
    void noVariables_templateUnchanged() {
        VariableContext ctx = new VariableContext();
        assertEquals("/api/v1/books", ctx.substitute("/api/v1/books"));
    }

    // ---------- Phase 2: built-in dynamic variables ----------

    @Test
    void dynamicUuid_isGenerated() {
        VariableContext ctx = new VariableContext();
        String first = ctx.substitute("${$UUID}");
        String second = ctx.substitute("${$UUID}");
        assertTrue(first.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertTrue(second.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        // Each reference generates a new UUID
        assertTrue(!first.equals(second));
    }

    @Test
    void dynamicTimestamp_isNumeric() {
        VariableContext ctx = new VariableContext();
        String ts = ctx.substitute("${$TIMESTAMP}");
        assertTrue(ts.matches("\\d+"));
    }

    @Test
    void dynamicFakerName_isNonEmpty() {
        VariableContext ctx = new VariableContext();
        String name = ctx.substitute("${$FAKER_NAME}");
        assertTrue(!name.isBlank());
        assertTrue(name.contains(" "));
    }

    @Test
    void dynamicFakerBookTitle_isNonEmpty() {
        VariableContext ctx = new VariableContext();
        String title = ctx.substitute("${$FAKER_BOOK_TITLE}");
        assertTrue(!title.isBlank());
    }

    @Test
    void dynamicFakerWord_isNonEmpty() {
        VariableContext ctx = new VariableContext();
        String word = ctx.substitute("${$FAKER_WORD}");
        assertTrue(!word.isBlank());
    }

    @Test
    void dynamicFakerEmail_hasAtSign() {
        VariableContext ctx = new VariableContext();
        String email = ctx.substitute("${$FAKER_EMAIL}");
        assertTrue(email.contains("@"));
    }

    // ---------- Phase 2: deep access into stored JSON value ----------

    @Test
    void deepAccess_arrayIndex() {
        VariableContext ctx = new VariableContext();
        ctx.put("books", "[{\"id\":\"b1\",\"title\":\"A\"},{\"id\":\"b2\",\"title\":\"B\"}]");
        assertEquals("b1", ctx.substitute("${books[0].id}"));
        assertEquals("b2", ctx.substitute("${books[1].id}"));
    }

    @Test
    void deep_access_arrayWildcard() {
        VariableContext ctx = new VariableContext();
        ctx.put("books", "[{\"id\":\"b1\"},{\"id\":\"b2\"}]");
        assertEquals("[\"b1\",\"b2\"]", ctx.substitute("${books[*].id}"));
    }

    @Test
    void deep_access_missingBaseVariable_returnsEmpty() {
        VariableContext ctx = new VariableContext();
        assertEquals("", ctx.substitute("${missing[0].id}"));
    }

    @Test
    void deep_access_wildcard_lastElement() {
        VariableContext ctx = new VariableContext();
        ctx.put("books", "[{\"id\":\"b1\"},{\"id\":\"b2\"}]");
        assertEquals("b2", ctx.substitute("${books[-1].id}"));
    }

    @Test
    void mixedBuiltinAndStored() {
        VariableContext ctx = new VariableContext();
        ctx.put("prefix", "book-");
        assertTrue(ctx.substitute("${prefix}${$UUID}").startsWith("book-"));
    }
}
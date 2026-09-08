package vn.edu.ptit.web_grading_system.executor_service.service;

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
}
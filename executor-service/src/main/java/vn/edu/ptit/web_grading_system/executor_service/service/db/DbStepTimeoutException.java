package vn.edu.ptit.web_grading_system.executor_service.service.db;

import java.sql.SQLException;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

/**
 * Signalled when a step's {@code timeoutMs} budget is exhausted —
 * while establishing the connection ({@link DbConnectionHelper}),
 * or between entries of a list-based step ({@code DB_MIGRATION}
 * statements, {@code DB_SCHEMA_CHECK} checks). The message already carries the
 * {@link Constant.Message.Db#SQL_TIMEOUT_ERROR} prefix; callers
 * surface it verbatim.
 */
public class DbStepTimeoutException extends SQLException {

    public DbStepTimeoutException(String message) {
        super(message);
    }
}

package vn.edu.ptit.web_grading_system.executor_service.service.db;

import java.sql.SQLException;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

/**
 * Signalled when a step's {@code timeoutMs} budget is exhausted —
 * either while establishing the connection or between statements
 * inside a {@code DB_MIGRATION}. The message already carries the
 * {@link Constant.Message.Db#SQL_TIMEOUT_ERROR} prefix; callers
 * surface it verbatim.
 */
public class DbStepTimeoutException extends SQLException {

    public DbStepTimeoutException(String message) {
        super(message);
    }
}

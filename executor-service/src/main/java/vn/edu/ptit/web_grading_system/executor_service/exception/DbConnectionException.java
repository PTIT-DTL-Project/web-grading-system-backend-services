package vn.edu.ptit.web_grading_system.executor_service.exception;

import java.sql.SQLException;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

/**
 * Signalled by {@link DbConnectionHelper} when the connect-retry
 * budget is exhausted. The message already carries the dialect hint
 * ({@link Constant.Message.Db#CONNECTION_DIALECT_PREFIX} …);
 * callers ({@link vn.edu.ptit.web_grading_system.executor_service.service.step.impl.DbStepResults#message(SQLException)})
 * surface it verbatim — never wrapped in {@code SQL_EXECUTION_ERROR}.
 */
public class DbConnectionException extends SQLException {

    public DbConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

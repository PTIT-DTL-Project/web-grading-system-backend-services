package vn.edu.ptit.web_grading_system.api_gateway.service;

import lombok.Builder;
import org.springframework.http.HttpStatus;

/**
 * A bulk-import failure, carrying the HTTP status and the machine code the frontend
 * maps to i18n — same envelope contract as {@link ChangePasswordException}.
 *
 * <p>Per-row failures never become this exception: a bad row lands in the report
 * and the batch continues. Only file-level problems (unreadable, over caps) and
 * authorization failures surface here.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
public class UserImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    // @Builder instead of a positional 3-arg constructor — project convention for
    // constructor calls with more than 2 arguments.
    @Builder
    private UserImportException(HttpStatus status, String code, Throwable cause) {
        super(code, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static UserImportException validationFailed() {
        return UserImportException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .code("validation_failed")
                .build();
    }

    public static UserImportException fileTooLarge() {
        return UserImportException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .code("file_too_large")
                .build();
    }

    public static UserImportException tooManyRows() {
        return UserImportException.builder()
                .status(HttpStatus.BAD_REQUEST)
                .code("too_many_rows")
                .build();
    }

    public static UserImportException forbidden() {
        return UserImportException.builder()
                .status(HttpStatus.FORBIDDEN)
                .code("forbidden")
                .build();
    }

    public static UserImportException identityProviderUnavailable(Throwable cause) {
        return UserImportException.builder()
                .status(HttpStatus.BAD_GATEWAY)
                .code("identity_provider_unavailable")
                .cause(cause)
                .build();
    }
}

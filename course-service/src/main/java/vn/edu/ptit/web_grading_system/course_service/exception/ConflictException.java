package vn.edu.ptit.web_grading_system.course_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Used when a mutation conflicts with the current state (HTTP 409). */
@Getter
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

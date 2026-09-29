package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;

public class InvalidFilterException extends BadRequestException {
    public InvalidFilterException(String message) {
        super(message);
    }
}

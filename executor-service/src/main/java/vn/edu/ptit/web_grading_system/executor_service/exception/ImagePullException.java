package vn.edu.ptit.web_grading_system.executor_service.exception;

public class ImagePullException extends RuntimeException {
    public ImagePullException(String message) { super(message); }
    public ImagePullException(String message, Throwable cause) { super(message, cause); }
}

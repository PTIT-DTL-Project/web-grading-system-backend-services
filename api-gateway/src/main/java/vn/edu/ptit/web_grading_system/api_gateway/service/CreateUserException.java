package vn.edu.ptit.web_grading_system.api_gateway.service;

/**
 * Keycloak refused a user-creation request. Unlike the shared client contract
 * (any error = provider trouble), the import flow needs the status to tell a
 * duplicate race ({@code 409}) and invalid input ({@code 400}) apart from real
 * provider failures — the service maps each to a different report entry.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
public class CreateUserException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public CreateUserException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}

package vn.edu.ptit.web_grading_system.executor_service.event;

/** Action discriminator for the shared wgs-events topic. */
public enum WgsEventAction {
    GRADE_SUBMISSION,
    UNKNOWN;

    public static WgsEventAction fromString(String action) {
        if (action == null) return GRADE_SUBMISSION; // pre-envelope message
        try { return valueOf(action); } catch (IllegalArgumentException e) { return UNKNOWN; }
    }
}
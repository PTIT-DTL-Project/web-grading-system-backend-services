package vn.edu.ptit.web_grading_system.submission_service.event;

/**
 * Action discriminator for the shared wgs-events topic. Add new actions here —
 * never create a new topic (Aiven free tier limits us to 5 topics).
 */
public enum WgsEventAction {
    GRADE_SUBMISSION,
    UNKNOWN;

    public static WgsEventAction fromString(String action) {
        if (action == null) return UNKNOWN;
        try { return valueOf(action); } catch (IllegalArgumentException e) { return UNKNOWN; }
    }
}

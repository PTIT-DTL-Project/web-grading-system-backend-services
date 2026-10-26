package vn.edu.ptit.web_grading_system.course_service.entity;

/**
 * GRADED assignments belong to a class and feed the EXERCISE score component.
 * PRACTICE exercises are class-independent (class_id NULL), visible to every
 * student once published, and never contribute to any class score.
 */
public enum AssignmentPurpose {
    GRADED,
    PRACTICE
}

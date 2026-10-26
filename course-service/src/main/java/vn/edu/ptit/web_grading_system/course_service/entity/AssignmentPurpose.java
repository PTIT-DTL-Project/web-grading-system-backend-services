package vn.edu.ptit.web_grading_system.course_service.entity;

/**
 * Review: 2026-10-26, practice-exercises feature
 * GRADED assignments belong to a class and feed the EXERCISE score component.
 * PRACTICE exercises are class-independent (class_id NULL), visible to every
 * student once published, and never contribute to any class score.
 */
public enum AssignmentPurpose {
    GRADED,
    PRACTICE
}

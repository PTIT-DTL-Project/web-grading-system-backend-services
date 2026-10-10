package vn.edu.ptit.web_grading_system.result_service.entity;

/**
 * Run scope of a result row, derived server-side from the report's planId
 * (executor/FE contracts unchanged): {@code FULL} = whole-assignment run
 * ({@code planId == null}, grades every plan), {@code PLAN} = single-plan run.
 *
 * <p>A FULL run supersedes every per-plan latest row of the same
 * (student, assignment) — averaging a FULL row together with an older
 * per-plan latest double-counts (2026-10-10, exercise score 5.00 for a 10).
 *
 * <p>Review: 2026-10-10, explicit run scope.
 */
public enum ResultScope {
    FULL,
    PLAN
}

package vn.edu.ptit.web_grading_system.course_service;

import java.util.Set;
import java.util.UUID;

/** Shared constants. Mirrors the executor-service {@code Constant} convention. */
public final class Constant {

    /** Principal that owns images registered before ownership was enforced (backfilled by V4). */
    public static final UUID IMAGE_LIBRARY_SYSTEM_OWNER =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static final class Image {
        /** Docker image reference grammar accepted by the library:
         *  {@code registry[:port]/repo:tag}  or  {@code name@sha256:<digest>}.
         *  The tag must be explicit (':latest' is forbidden) for grading reproducibility.
         *  Registry host may include ':port'; userinfo ('user:pass@') is rejected. */
        public static final String IMAGE_URL_REGEX =
                "^(?!.*:latest$)(?!.*@.*@)" +
                "(?:[a-zA-Z0-9][a-zA-Z0-9.-]*(?::[0-9]{1,5})?/[a-zA-Z0-9][a-zA-Z0-9._/-]*|" +
                "[a-zA-Z0-9][a-zA-Z0-9._-]*)" +
                "(?::[A-Za-z0-9][A-Za-z0-9._-]*|@sha256:[0-9a-fA-F]{64})$";
        public static final int NAME_MAX = 255;
        public static final int IMAGE_URL_MAX = 500;
        public static final String INVALID_URL_MESSAGE =
                "image_url must be registry/repo:tag (explicit tag, ':latest' forbidden) " +
                "or name@sha256:<digest>";

        private Image() {}
    }

    // Review: 2026-09-29, Pullfrog PR — rewrote Javadoc to say "JPA attribute names"
    // instead of "Database column names". root.get() resolves against the entity
    // attribute path, so "owner_id" would throw at runtime; the correct value is
    // "ownerId" (attribute), not the mapped column name.
    public static final class CourseClassAttr {
        public static final String OWNER_ID = "ownerId";
        public static final String NAME     = "name";
        public static final String SEMESTER = "semester";
        public static final String STATUS   = "status";

        /** Fields accepted by the structured search parser for this entity. */
        public static final Set<String> SEARCHABLE = Set.of(NAME, SEMESTER);

        private CourseClassAttr() {}
    }

    private Constant() {}
}

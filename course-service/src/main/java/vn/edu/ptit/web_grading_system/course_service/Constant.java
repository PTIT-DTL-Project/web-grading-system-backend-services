package vn.edu.ptit.web_grading_system.course_service;

import java.util.Set;

/** Shared constants. Mirrors the executor-service {@code Constant} convention. */
public final class Constant {

    public static final class CourseClassAttr {
        public static final String ID       = "id";
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

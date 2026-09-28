package vn.edu.ptit.web_grading_system.executor_service.config;

/**
 * Packaging conventions adopted for executor-service, recorded here
 * as a class rather than in a public design document.
 *
 * <p>Any new team member adding a step executor, dialect, or Docker
 * concern should check the fields below before choosing a package.
 */
public class PackagingConvention {

    /** Grouping axis is phase-first.
     *
     * <p>{@code grading/} is the sole orchestrator and is the only
     * package that imports {@code service/docker/}; {@code service/db/}
     * supports the step executors and the orchestrator.
     * {@code service/step/}, {@code docker/}, {@code db/} are capability
     * collaborators, not a second axis. Do not promote {@code db/} or
     * {@code docker/} out of {@code service/} — they are business logic,
     * not infrastructure layers.
     */
    public final String groupingAxis;

    /** Impl-suffix convention.
     *
     * <p>When a single implementation exists, keep the {@code Impl}
     * suffix ({@code DockerImageGatewayImpl}). When several
     * implementations exist, give each a descriptive variant name
     * instead ({@code MysqlDialect}, {@code HttpStepExecutor}). Do
     * not rename a lone impl to match its interface's simple name —
     * the two then share a simple name in adjacent packages and the
     * consumer must disambiguate which it means.
     */
    public final String implSuffixConvention;

    /** Free-form rationale for the two rules above. */
    public final String rationale;

    public PackagingConvention(String groupingAxis,
                               String implSuffixConvention,
                               String rationale) {
        this.groupingAxis = groupingAxis;
        this.implSuffixConvention = implSuffixConvention;
        this.rationale = rationale;
    }

    /** The single shared instance for this service. */
    public static final PackagingConvention INSTANCE = new PackagingConvention(
            "phase-first: grading/ is the sole orchestrator and the only"
            + " package importing service/docker/; db/ and docker/ stay as"
            + " capability collaborators inside service/",
            "one impl -> keep Impl suffix; several -> variant names"
            + " (MysqlDialect, HttpStepExecutor, etc.); do not rename a"
            + " lone impl to match its interface's simple name",
            "Phase-first was chosen to keep the grading pipeline readable;"
            + " the grouping question is a documented follow-up, not a gate."
            + " See executor-grading SKILL.md §11 history.");
}

package vn.edu.ptit.web_grading_system.api_gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulk account import for the system admin (UC-17): one CSV row becomes one
 * Keycloak user with a temporary initial password and a forced
 * {@code UPDATE_PASSWORD} change on first login.
 *
 * <p>CSV columns: {@code username, fullName, email, role?} — {@code role} is
 * {@code STUDENT} or {@code LECTURER} (optional {@code ROLE_} prefix tolerated),
 * blank means {@code STUDENT} (secure default: the lower privilege). Unknown
 * role values fail the row, never the batch.
 *
 * <p>Per row, in order: validate → duplicate check by username, then email
 * (skip with reason) → create → temporary password (the username itself) →
 * realm role from a per-import cache. One bad row lands in the report and the
 * batch continues; only file-level problems abort the whole request, and
 * re-running a file is safe (created rows skip as duplicates).
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportService {

    /** Caps bulk imports: sequential admin calls per row add up. */
    static final int MAX_ROWS = 2000;
    /** Caps bulk imports: rejects oversized uploads before parsing. */
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final KeycloakAdminClient keycloak;

    /** One imported row: 1-based line number, outcome and machine-readable reason. */
    public record RowResult(int row, String username, String role, String outcome, String reason) {
        static RowResult created(int row, String username, String role) {
            return new RowResult(row, username, role, "created", null);
        }

        static RowResult skipped(int row, String username, String role, String reason) {
            return new RowResult(row, username, role, "skipped", reason);
        }

        static RowResult failed(int row, String username, String role, String reason) {
            return new RowResult(row, username, role, "failed", reason);
        }
    }

    /** Batch report. {@code created} counts per normalized role. */
    public record ImportSummary(Map<String, Integer> created, int skipped, List<RowResult> failed) {
    }

    public Mono<ImportSummary> importUsers(FilePart file) {
        String fileName = file.filename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            return Mono.error(UserImportException.validationFailed());
        }
        return readBytes(file)
                .flatMap(bytes -> {
                    if (bytes.length == 0) {
                        return Mono.error(UserImportException.validationFailed());
                    }
                    List<String[]> rows = parseCsv(bytes);
                    if (rows.size() > MAX_ROWS) {
                        return Mono.error(UserImportException.tooManyRows());
                    }
                    return runImport(rows);
                });
    }

    private Mono<byte[]> readBytes(FilePart file) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return file.content()
                .map(buffer -> {
                    byte[] chunk = new byte[buffer.readableByteCount()];
                    buffer.read(chunk);
                    return chunk;
                })
                .doOnNext(chunk -> {
                    if (out.size() + chunk.length > MAX_BYTES) {
                        throw UserImportException.fileTooLarge();
                    }
                    out.write(chunk, 0, chunk.length);
                })
                .then(Mono.fromSupplier(out::toByteArray));
    }

    /**
     * CSV discipline mirrors {@code ClassService.parseCsv}: blank lines skipped,
     * naive comma split (no quoting library), header detected by first-column
     * heuristic. Kept dependency-free on purpose — same rationale, same shape.
     */
    static List<String[]> parseCsv(byte[] bytes) {
        List<String[]> rows = new ArrayList<>();
        String text = new String(bytes, StandardCharsets.UTF_8);
        boolean first = true;
        for (String line : text.split("\r?\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (first && (cols[0].trim().equalsIgnoreCase("username")
                    || cols[0].trim().equalsIgnoreCase("studentcode")
                    || cols[0].trim().equalsIgnoreCase("code"))) {
                first = false;
                continue;
            }
            first = false;
            rows.add(cols);
        }
        return rows;
    }

    private Mono<ImportSummary> runImport(List<String[]> rows) {
        // Both realm roles are resolved once per import: per-row lookups would
        // multiply admin calls for no benefit on a two-value domain. If role
        // resolution fails the whole batch aborts — without roles nothing can
        // be provisioned correctly.
        Mono<Map<String, Map<String, String>>> roles = Mono.zip(
                        keycloak.findRealmRole("ROLE_STUDENT"),
                        keycloak.findRealmRole("ROLE_LECTURER"))
                .map(tuple -> Map.of("STUDENT", tuple.getT1(), "LECTURER", tuple.getT2()))
                .onErrorMap(error -> UserImportException.identityProviderUnavailable(error));
        // Sequential on purpose: deterministic report order and gentle load on
        // Keycloak for a rare admin operation.
        return roles.flatMap(roleReps -> Flux.fromIterable(rows)
                .index()
                .concatMap(indexed -> importRow(
                        indexed.getT1().intValue() + 1, indexed.getT2(), roleReps))
                .collectList()
                .map(UserImportService::summarize));
    }

    private Mono<RowResult> importRow(int line, String[] cols, Map<String, Map<String, String>> roleReps) {
        if (cols.length < 3) {
            return Mono.just(RowResult.failed(line, cell(cols, 0), "STUDENT", "not_enough_columns"));
        }
        String username = cols[0].trim();
        String fullName = cols[1].trim();
        String email = cols[2].trim();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            return Mono.just(RowResult.failed(line, username, "STUDENT", "blank_username_or_email"));
        }
        String role = normalizeRole(cols.length > 3 ? cols[3] : "");
        if (role == null) {
            return Mono.just(RowResult.failed(line, username, cols.length > 3 ? cols[3].trim() : "",
                    "unknown_role"));
        }
        Map<String, String> roleRep = roleReps.get(role);
        if (roleRep == null) {
            return Mono.error(UserImportException.identityProviderUnavailable(
                    new IllegalStateException("role representation missing for " + role)));
        }
        // findUserId completes empty when Keycloak knows nobody by that value —
        // empty is "absent", never an error, so duplicates skip cleanly.
        return keycloak.findUserId(username)
                .flatMap(id -> Mono.just(RowResult.skipped(line, username, role, "duplicate_username")))
                .switchIfEmpty(Mono.defer(() -> keycloak.findUserId(email)
                        .flatMap(id -> Mono.just(RowResult.skipped(line, username, role, "duplicate_email")))
                        .switchIfEmpty(Mono.defer(() -> createAndProvision(
                                line, username, fullName, email, role, roleRep)))))
                .onErrorResume(error -> error instanceof UserImportException
                        ? Mono.error(error)
                        : Mono.just(RowResult.failed(line, username, role, "provider_error")));
    }

    private Mono<RowResult> createAndProvision(int line, String username, String fullName,
            String email, String role, Map<String, String> roleRep) {
        return keycloak.createUser(username, email, fullName)
                .flatMap(userId -> keycloak.setTemporaryPassword(userId, username)
                        .flatMap(applied -> {
                            if (!applied) {
                                return Mono.just(RowResult.failed(line, username, role, "weak_password"));
                            }
                            return keycloak.assignRealmRoles(userId, List.of(roleRep))
                                    .thenReturn(RowResult.created(line, username, role))
                                    // The user now exists with a password but possibly no
                                    // role — re-running skips as duplicate, so the report
                                    // must name the exact gap for manual repair.
                                    .onErrorResume(error -> Mono.just(RowResult.failed(
                                            line, username, role, "role_not_assigned")));
                        }))
                .onErrorResume(CreateUserException.class, error -> Mono.just(
                        error.status() == 409
                                ? RowResult.skipped(line, username, role, "duplicate_username")
                                : error.status() == 400
                                        ? RowResult.failed(line, username, role, "invalid_input")
                                        : RowResult.failed(line, username, role, "provider_error")));
    }

    /** Blank → STUDENT (secure default); optional ROLE_ prefix tolerated. */
    static String normalizeRole(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if (value.isEmpty()) {
            return "STUDENT";
        }
        if (value.startsWith("ROLE_")) {
            value = value.substring("ROLE_".length());
        }
        return value.equals("STUDENT") || value.equals("LECTURER") ? value : null;
    }

    private static String cell(String[] cols, int index) {
        return index < cols.length ? cols[index].trim() : "";
    }

    private static ImportSummary summarize(List<RowResult> results) {
        Map<String, Integer> created = new LinkedHashMap<>(Map.of("STUDENT", 0, "LECTURER", 0));
        int skipped = 0;
        List<RowResult> failed = new ArrayList<>();
        for (RowResult row : results) {
            switch (row.outcome()) {
                case "created" -> created.compute(row.role(),
                        (role, count) -> count == null ? 1 : count + 1);
                case "skipped" -> skipped++;
                default -> failed.add(row);
            }
        }
        return new ImportSummary(created, skipped, failed);
    }
}

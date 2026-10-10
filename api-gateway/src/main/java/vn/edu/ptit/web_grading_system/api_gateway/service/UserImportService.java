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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    // Mirrored client-side in AdminUsersPage (MAX_BYTES/MAX_ROWS) for instant
    // feedback — the server stays the source of truth; keep both in sync.
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
        // One shared admin token for the whole batch (the session memoizes it),
        // and role representations resolved lazily: a student-only import never
        // touches ROLE_LECTURER, so its absence or unreadability cannot abort it.
        // Sequential on purpose: deterministic report order and gentle load on
        // Keycloak for a rare admin operation; the plain HashMap cache below is
        // safe exactly because of that sequencing.
        KeycloakAdminClient.BulkOperations admin = keycloak.bulk();
        Map<String, Map<String, String>> roleCache = new HashMap<>();
        return Flux.fromIterable(rows)
                .index()
                .concatMap(indexed -> importRow(
                        indexed.getT1().intValue() + 1, indexed.getT2(), admin, roleCache))
                .collectList()
                .map(UserImportService::summarize);
    }

    private Mono<Map<String, String>> roleRep(KeycloakAdminClient.BulkOperations admin,
            Map<String, Map<String, String>> roleCache, String role) {
        Map<String, String> cached = roleCache.get(role);
        if (cached != null) {
            return Mono.just(cached);
        }
        return admin.findRealmRole("ROLE_" + role)
                .doOnNext(rep -> roleCache.put(role, rep));
    }

    private Mono<RowResult> importRow(int line, String[] cols,
            KeycloakAdminClient.BulkOperations admin, Map<String, Map<String, String>> roleCache) {
        if (cols.length < 3) {
            return Mono.just(RowResult.failed(line, cell(cols, 0), "STUDENT", "not_enough_columns"));
        }
        // Keycloak stores usernames lowercased but keeps the password verbatim:
        // importing "B22DCCN001" as-is mints user b22dccn001 with password
        // B22DCCN001 — unguessable. Normalize once here so lookup, creation
        // and the initial password all use the stored form.
        // Review: 2026-10-10, uppercase-CSV login mismatch.
        String username = cols[0].trim().toLowerCase(Locale.ROOT);
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
        // findUserId completes empty when Keycloak knows nobody by that value —
        // empty is "absent", never an error, so duplicates skip cleanly. The
        // role lookup is lazy per row: an unknown role name fails this row
        // only (unknown_role below covers typos before any call is made, and
        // a provider-side role failure surfaces as provider_error for the row,
        // never as a batch abort).
        return admin.findUserId(username)
                .flatMap(id -> Mono.just(RowResult.skipped(line, username, role, "duplicate_username")))
                .switchIfEmpty(Mono.defer(() -> admin.findUserId(email)
                        .flatMap(id -> Mono.just(RowResult.skipped(line, username, role, "duplicate_email")))
                        .switchIfEmpty(Mono.defer(() -> roleRep(admin, roleCache, role)
                                .flatMap(rep -> createAndProvision(
                                        admin, line, username, fullName, email, role, rep))))))
                .onErrorResume(error -> error instanceof UserImportException
                        ? Mono.error(error)
                        : Mono.just(RowResult.failed(line, username, role, "provider_error")));
    }

    private Mono<RowResult> createAndProvision(KeycloakAdminClient.BulkOperations admin, int line,
            String username, String fullName, String email, String role, Map<String, String> roleRep) {
        // The realm's user profile requires non-blank firstName AND lastName;
        // a blank fullName falls back to the username for both (always
        // non-blank here) rather than rejecting the row — the name is
        // cosmetic, the account is not.
        // Review: 2026-10-10, missing-lastName login wall; Pullfrog: firstName
        // needs the same fallback, the wall demands all required fields.
        String[] names = splitName(fullName);
        String firstName = StringUtils.hasText(names[0]) ? names[0] : username;
        String lastName = StringUtils.hasText(names[1]) ? names[1] : username;
        return admin.createUser(username, email, firstName, lastName)
                .flatMap(userId -> admin.setTemporaryPassword(userId, username)
                        .flatMap(applied -> {
                            if (!applied) {
                                return Mono.just(RowResult.failed(line, username, role, "weak_password"));
                            }
                            return admin.assignRealmRoles(userId, List.of(roleRep))
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

    /**
     * Splits a Vietnamese full name into {@code [firstName, lastName]}: the
     * first token is the family name ({@code lastName}), the rest is the given
     * name ({@code firstName}). A single token fills both (still non-blank);
     * blank fills neither — the caller falls back to the username for both,
     * which the realm profile requires non-blank.
     *
     * <p>Review: 2026-10-10, missing-lastName login wall.
     */
    static String[] splitName(String fullName) {
        String[] tokens = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        if (tokens.length == 0 || (tokens.length == 1 && tokens[0].isEmpty())) {
            return new String[] {"", ""};
        }
        if (tokens.length == 1) {
            return new String[] {tokens[0], tokens[0]};
        }
        return new String[] {
                String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length)),
                tokens[0]};
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

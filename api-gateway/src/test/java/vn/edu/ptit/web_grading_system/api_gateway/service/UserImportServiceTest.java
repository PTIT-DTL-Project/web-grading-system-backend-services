package vn.edu.ptit.web_grading_system.api_gateway.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the import orchestration with a mocked {@link KeycloakAdminClient}: a
 * mixed batch (new student, new lecturer, duplicate username, duplicate email,
 * unknown role, short row) must report each row correctly while the batch runs
 * to the end, and role representations resolve lazily once per role, never for
 * roles the batch does not use.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
class UserImportServiceTest {

    private final KeycloakAdminClient keycloak = Mockito.mock(KeycloakAdminClient.class);
    private final KeycloakAdminClient.BulkOperations ops =
            Mockito.mock(KeycloakAdminClient.BulkOperations.class);
    private final UserImportService service = new UserImportService(keycloak);

    private void stubSession() {
        Mockito.when(keycloak.bulk()).thenReturn(ops);
        // The session delegates to the same stubbed answers: existing tests
        // keep stubbing/verify on the ops mock below.
    }

    private static FilePart filePart(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(bytes);
        FilePart part = Mockito.mock(FilePart.class);
        Mockito.when(part.filename()).thenReturn("users.csv");
        Mockito.when(part.content()).thenReturn(Flux.just(buffer));
        return part;
    }

    @Test
    void mixedBatch_reportsEachRowAndCachesRoleLookups() {
        stubSession();
        Mockito.when(ops.findRealmRole("ROLE_STUDENT"))
                .thenReturn(Mono.just(Map.of("id", "s", "name", "ROLE_STUDENT")));
        Mockito.when(ops.findRealmRole("ROLE_LECTURER"))
                .thenReturn(Mono.just(Map.of("id", "l", "name", "ROLE_LECTURER")));
        // Generic empty first: Mockito matches later stubs first, so the two
        // specific duplicates below still resolve while everything else is absent.
        Mockito.when(ops.findUserId(Mockito.anyString()))
                .thenReturn(Mono.empty());
        // Row 2 collides on username, row 3 on email; the rest are new.
        Mockito.when(ops.findUserId("B22DCCN001"))
                .thenReturn(Mono.just("existing-id"));
        Mockito.when(ops.findUserId("taken@ptit.edu.vn"))
                .thenReturn(Mono.just("other-id"));
        Mockito.when(ops.createUser(Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just("new-id"));
        Mockito.when(ops.setTemporaryPassword(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(ops.assignRealmRoles(Mockito.anyString(), Mockito.any()))
                .thenReturn(Mono.empty());
        String csv = String.join("\n",
                "username,fullName,email,role",
                "B22DCCN001,Taken User,taken@ptit.edu.vn,STUDENT",
                "gv001,Giang Vien,gv@ptit.edu.vn,LECTURER",
                "B22DCCN002,New Student,taken@ptit.edu.vn,",
                "B22DCCN003,Bad Role,bad@ptit.edu.vn,ADMIN",
                "only-one-column");

        StepVerifier.create(service.importUsers(filePart(csv)))
                .assertNext(summary -> {
                    assertThat(summary.created()).isEqualTo(Map.of("STUDENT", 0, "LECTURER", 1));
                    assertThat(summary.skipped()).isEqualTo(2);
                    assertThat(summary.failed()).hasSize(2);
                    assertThat(summary.failed().get(0).reason()).isEqualTo("unknown_role");
                    assertThat(summary.failed().get(1).reason()).isEqualTo("not_enough_columns");
                })
                .verifyComplete();

        // Lazy roles: only ROLE_LECTURER is resolved (once) because the two
        // student rows skip as duplicates before any role lookup, while the
        // lecturer row is the single new user needing one.
        Mockito.verify(ops, Mockito.never()).findRealmRole("ROLE_STUDENT");
        Mockito.verify(ops, Mockito.times(1)).findRealmRole("ROLE_LECTURER");
        // The duplicate-username row short-circuits before creation.
        Mockito.verify(ops, Mockito.never()).createUser(
                Mockito.eq("B22DCCN001"), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());
        // The created lecturer row splits the Vietnamese name: family name
        // first token → lastName, rest → firstName.
        org.mockito.ArgumentCaptor<String> firstCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> lastCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.verify(ops).createUser(Mockito.eq("gv001"), Mockito.anyString(),
                firstCaptor.capture(), lastCaptor.capture());
        assertThat(firstCaptor.getValue()).isEqualTo("Vien");
        assertThat(lastCaptor.getValue()).isEqualTo("Giang");
        // Exactly one bulk session per import.
        Mockito.verify(keycloak, Mockito.times(1)).bulk();
    }

    @Test
    void studentOnlyBatch_neverResolvesLecturerRole() {
        stubSession();
        Mockito.when(ops.findRealmRole("ROLE_STUDENT"))
                .thenReturn(Mono.just(Map.of("id", "s", "name", "ROLE_STUDENT")));
        Mockito.when(ops.findUserId(Mockito.anyString())).thenReturn(Mono.empty());
        Mockito.when(ops.createUser(Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just("new-id"));
        Mockito.when(ops.setTemporaryPassword(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(ops.assignRealmRoles(Mockito.anyString(), Mockito.any()))
                .thenReturn(Mono.empty());
        String csv = String.join("\n",
                "username,fullName,email,role",
                "B22DCCN010,Student Ten,a@ptit.edu.vn,",
                "B22DCCN011,Student Eleven,b@ptit.edu.vn,STUDENT");

        StepVerifier.create(service.importUsers(filePart(csv)))
                .assertNext(summary -> {
                    assertThat(summary.created()).isEqualTo(Map.of("STUDENT", 2, "LECTURER", 0));
                    assertThat(summary.failed()).isEmpty();
                })
                .verifyComplete();

        // The unused lecturer role is never touched: its absence cannot abort
        // a student-only import.
        Mockito.verify(ops, Mockito.never()).findRealmRole("ROLE_LECTURER");
        Mockito.verify(ops, Mockito.times(1)).findRealmRole("ROLE_STUDENT");
    }

    @Test
    void uppercaseUsername_normalizedToLowercaseEverywhere() {
        stubSession();
        Mockito.when(ops.findRealmRole("ROLE_STUDENT"))
                .thenReturn(Mono.just(Map.of("id", "s", "name", "ROLE_STUDENT")));
        Mockito.when(ops.findUserId(Mockito.anyString())).thenReturn(Mono.empty());
        Mockito.when(ops.createUser(Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just("new-id"));
        Mockito.when(ops.setTemporaryPassword(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(ops.assignRealmRoles(Mockito.anyString(), Mockito.any()))
                .thenReturn(Mono.empty());
        String csv = String.join("\n",
                "username,fullName,email,role",
                "B22DCCN099,Nguyen Van Bay,b22dccn099@ptit.edu.vn,");

        StepVerifier.create(service.importUsers(filePart(csv)))
                .assertNext(summary -> assertThat(summary.failed()).isEmpty())
                .verifyComplete();

        // Keycloak stores the user lowercased: lookup, creation and the
        // initial password must all use the normalized form, or the password
        // (verbatim) stops matching the stored username.
        Mockito.verify(ops).findUserId("b22dccn099");
        Mockito.verify(ops).createUser(Mockito.eq("b22dccn099"), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString());
        org.mockito.ArgumentCaptor<String> passwordCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.verify(ops).setTemporaryPassword(Mockito.eq("new-id"), passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).isEqualTo("b22dccn099");
    }

    @Test
    void blankRole_defaultsToStudent() {
        assertThat(UserImportService.normalizeRole("")).isEqualTo("STUDENT");
        assertThat(UserImportService.normalizeRole("  role_lecturer ")).isEqualTo("LECTURER");
        assertThat(UserImportService.normalizeRole("ADMIN")).isNull();
    }

    @Test
    void splitName_vietnameseFamilyNameFirst() {
        assertThat(UserImportService.splitName("Nguyen Van An"))
                .isEqualTo(new String[] {"Van An", "Nguyen"});
        assertThat(UserImportService.splitName("  Tran   Binh  "))
                .isEqualTo(new String[] {"Binh", "Tran"});
        assertThat(UserImportService.splitName("An"))
                .isEqualTo(new String[] {"An", "An"});
        assertThat(UserImportService.splitName("   "))
                .isEqualTo(new String[] {"", ""});
        assertThat(UserImportService.splitName(null))
                .isEqualTo(new String[] {"", ""});
    }

    @Test
    void blankFullName_fallsBackToUsernameForBothNames() {
        stubSession();
        Mockito.when(ops.findRealmRole("ROLE_STUDENT"))
                .thenReturn(Mono.just(Map.of("id", "s", "name", "ROLE_STUDENT")));
        Mockito.when(ops.findUserId(Mockito.anyString())).thenReturn(Mono.empty());
        Mockito.when(ops.createUser(Mockito.anyString(), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just("new-id"));
        Mockito.when(ops.setTemporaryPassword(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(ops.assignRealmRoles(Mockito.anyString(), Mockito.any()))
                .thenReturn(Mono.empty());
        // The row is created (not rejected): both names fall back to the
        // username so the realm's required-first/lastName profile is satisfied.
        String csv = String.join("\n",
                "username,fullName,email,role",
                "B22DCCN012,,c@ptit.edu.vn,");

        StepVerifier.create(service.importUsers(filePart(csv)))
                .assertNext(summary -> assertThat(summary.failed()).isEmpty())
                .verifyComplete();

        org.mockito.ArgumentCaptor<String> firstCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> lastCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.verify(ops).createUser(Mockito.eq("b22dccn012"), Mockito.anyString(),
                firstCaptor.capture(), lastCaptor.capture());
        assertThat(firstCaptor.getValue()).isEqualTo("b22dccn012");
        assertThat(lastCaptor.getValue()).isEqualTo("b22dccn012");
    }

    @Test
    void nonCsvFile_isRejected() {
        FilePart part = Mockito.mock(FilePart.class);
        Mockito.when(part.filename()).thenReturn("users.txt");

        StepVerifier.create(service.importUsers(part))
                .verifyErrorMatches(error -> error instanceof UserImportException
                        && ((UserImportException) error).code().equals("validation_failed"));
    }
}

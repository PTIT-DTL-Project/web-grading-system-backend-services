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
 * to the end, and role representations resolve once per import, not per row.
 *
 * <p>Review: 2026-10-09, bulk user import plan.
 */
class UserImportServiceTest {

    private final KeycloakAdminClient keycloak = Mockito.mock(KeycloakAdminClient.class);
    private final UserImportService service = new UserImportService(keycloak);

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
        Mockito.when(keycloak.findRealmRole("ROLE_STUDENT"))
                .thenReturn(Mono.just(Map.of("id", "s", "name", "ROLE_STUDENT")));
        Mockito.when(keycloak.findRealmRole("ROLE_LECTURER"))
                .thenReturn(Mono.just(Map.of("id", "l", "name", "ROLE_LECTURER")));
        // Generic empty first: Mockito matches later stubs first, so the two
        // specific duplicates below still resolve while everything else is absent.
        Mockito.when(keycloak.findUserId(Mockito.anyString()))
                .thenReturn(Mono.empty());
        // Row 2 collides on username, row 3 on email; the rest are new.
        Mockito.when(keycloak.findUserId("B22DCCN001"))
                .thenReturn(Mono.just("existing-id"));
        Mockito.when(keycloak.findUserId("taken@ptit.edu.vn"))
                .thenReturn(Mono.just("other-id"));
        Mockito.when(keycloak.createUser(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just("new-id"));
        Mockito.when(keycloak.setTemporaryPassword(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(Boolean.TRUE));
        Mockito.when(keycloak.assignRealmRoles(Mockito.anyString(), Mockito.any()))
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

        // One lookup per realm role for the whole import, not per row.
        Mockito.verify(keycloak, Mockito.times(1)).findRealmRole("ROLE_STUDENT");
        Mockito.verify(keycloak, Mockito.times(1)).findRealmRole("ROLE_LECTURER");
        // The duplicate-username row short-circuits before creation.
        Mockito.verify(keycloak, Mockito.never()).createUser(
                Mockito.eq("B22DCCN001"), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void blankRole_defaultsToStudent() {
        assertThat(UserImportService.normalizeRole("")).isEqualTo("STUDENT");
        assertThat(UserImportService.normalizeRole("  role_lecturer ")).isEqualTo("LECTURER");
        assertThat(UserImportService.normalizeRole("ADMIN")).isNull();
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

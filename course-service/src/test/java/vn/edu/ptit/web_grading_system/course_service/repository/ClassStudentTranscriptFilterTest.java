package vn.edu.ptit.web_grading_system.course_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the transcript roster filter added for large classes (H2 with
 * ddl-auto=create-drop, same convention as {@link ClassStudentLinkTest}). The JPQL
 * {@code escape '\'} clause and the code/name contains match are what the transcript
 * tab relies on; a broken pattern would silently show an empty table.
 *
 * <p>Review: 2026-11-12 — lecturer request: a 100-student class needed a student filter.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testtranscript"
})
class ClassStudentTranscriptFilterTest {

    @Autowired private ClassStudentRepository repository;

    private void seed(UUID classId) {
        repository.save(ClassStudent.builder()
                .classId(classId).studentCode("B22DCKH024").studentName("Vũ Công Tuấn Dương").build());
        repository.save(ClassStudent.builder()
                .classId(classId).studentCode("B22DCKH022").studentName("Mai Tùng Dương").build());
        repository.save(ClassStudent.builder()
                .classId(classId).studentCode("B22DCKH001").studentName("Nguyễn Văn An").build());
    }

    private long count(UUID classId, String pattern) {
        return repository.findByClassIdFiltered(classId, pattern, PageRequest.of(0, 20)).getTotalElements();
    }

    @Test
    @Transactional
    void nullPatternReturnsTheWholeRoster() {
        UUID classId = UUID.randomUUID();
        seed(classId);

        assertThat(count(classId, null)).isEqualTo(3);
    }

    @Test
    @Transactional
    void filtersByStudentCodeContainsCaseInsensitively() {
        UUID classId = UUID.randomUUID();
        seed(classId);

        assertThat(count(classId, "%b22dckh02%")).isEqualTo(2);
        assertThat(count(classId, "%b22dckh001%")).isEqualTo(1);
        assertThat(count(classId, "%unknown%")).isZero();
    }

    @Test
    @Transactional
    void filtersByNameContainsCaseInsensitively() {
        UUID classId = UUID.randomUUID();
        seed(classId);

        assertThat(count(classId, "%nguyễn văn%")).isEqualTo(1);
        assertThat(count(classId, "%dương%")).isEqualTo(2);
        assertThat(count(classId, "%mai tùng%")).isEqualTo(1);
    }

    @Test
    @Transactional
    void typedWildcardsStayLiteral() {
        UUID classId = UUID.randomUUID();
        seed(classId);

        // "%" alone must not behave like "match everything".
        assertThat(count(classId, "%\\%%")).isZero();
        assertThat(count(classId, "%\\_%")).isZero();
    }

    @Test
    @Transactional
    void doesNotLeakStudentsFromAnotherClass() {
        UUID classId = UUID.randomUUID();
        UUID otherClassId = UUID.randomUUID();
        seed(classId);
        repository.save(ClassStudent.builder()
                .classId(otherClassId).studentCode("B22DCKH999").studentName("Nguyễn Văn An").build());

        assertThat(count(classId, "%nguyễn văn an%")).isEqualTo(1);
    }
}

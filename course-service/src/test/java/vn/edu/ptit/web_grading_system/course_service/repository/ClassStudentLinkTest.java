package vn.edu.ptit.web_grading_system.course_service.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review: 2026-10-08 — student identity linking. Runs with flyway disabled and
 * ddl-auto=create-drop on H2, same convention as {@link DockerImageRepositoryTest}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testlink"
})
class ClassStudentLinkTest {

    @Autowired private ClassStudentRepository studentRepo;
    @Autowired private CourseClassRepository classRepo;
    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    void linkStudentIdentity_linksOnlyMatchingUnlinkedRows() {
        UUID classId = classRepo.save(CourseClass.builder()
                .ownerId(UUID.randomUUID()).name("Lop A").semester("20261").build()).getId();
        UUID newcomer = UUID.randomUUID();
        UUID incumbent = UUID.randomUUID();

        ClassStudent unlinked = studentRepo.save(ClassStudent.builder()
                .classId(classId).studentCode("S1").studentName("Mot")
                .email("Student@PTIT.edu.vn").build());
        ClassStudent owned = studentRepo.save(ClassStudent.builder()
                .classId(classId).studentCode("S2").studentName("Hai")
                .email("student@ptit.edu.vn").studentUserId(incumbent).build());
        ClassStudent otherMail = studentRepo.save(ClassStudent.builder()
                .classId(classId).studentCode("S3").studentName("Ba")
                .email("other@ptit.edu.vn").build());

        assertThat(studentRepo.linkStudentIdentity(newcomer, "student@ptit.edu.vn")).isEqualTo(1);
        // Bulk updates bypass the persistence context — clear it so the reads
        // below observe the database, not the stale managed instances.
        entityManager.clear();
        assertThat(studentRepo.findById(unlinked.getId()).orElseThrow().getStudentUserId())
                .isEqualTo(newcomer);
        assertThat(studentRepo.findById(owned.getId()).orElseThrow().getStudentUserId())
                .isEqualTo(incumbent);
        assertThat(studentRepo.findById(otherMail.getId()).orElseThrow().getStudentUserId())
                .isNull();
    }
}

package vn.edu.ptit.web_grading_system.executor_service.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ptit.web_grading_system.executor_service.entities.DockerImageState;
import vn.edu.ptit.web_grading_system.executor_service.entities.ImageScanStatus;

import javax.sql.DataSource;
import java.util.Properties;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 slice that validates the V7 {@code docker_image_state} DDL, the
 * entity's {@code @SQLRestriction} and the age-based prune query. Runs
 * with flyway disabled and ddl-auto=create-drop; no Flyway is executed
 * by any test in this repo, so the partial unique index is a production-PG
 * concern that is <em>not</em> covered by any automated test.
 *
 * Review: 2026-09-27, Pullfrog PR #21
 */
@SpringBootTest(classes = DockerImageStateRepositoryTest.JpaSlice.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:test"
})
class DockerImageStateRepositoryTest {

    @Autowired private DockerImageStateRepository repo;
    @PersistenceContext private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;

    /** Minimal context: JPA + transaction only. No component scan, so the
     * Kafka listener wiring that needs the gitignored docker/kafka-ca.pem
     * on CI is never pulled in (PR #21). */
    @Configuration
    @EnableJpaRepositories(basePackageClasses = DockerImageStateRepository.class)
    static class JpaSlice {
        @Bean
        DataSource dataSource() {
            com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:test");
            return ds;
        }
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan(
                    DockerImageState.class.getPackage().getName());
            em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            em.setPersistenceUnitName("test");
            Properties props = new Properties();
            props.put("hibernate.hbm2ddl.auto", "create-drop");
            props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            props.put("hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
            em.setJpaProperties(props);
            return em;
        }
        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory em) {
            return new JpaTransactionManager(em);
        }
    }

    @Test
    @Transactional
    void saveAndFindByImageUrlAndPodId() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("ghcr.io/org/app:v1.0").podId("pod-a")
                .status(ImageScanStatus.PULLED).build();
        repo.save(row);
        assertThat(repo.findByImageUrlAndPodId("ghcr.io/org/app:v1.0", "pod-a"))
                .hasValueSatisfying(s ->
                        assertThat(s.getStatus()).isEqualTo(ImageScanStatus.PULLED));
    }

    @Test
    @Transactional
    void findExcludesSoftDeleted() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("b").status(ImageScanStatus.FAILED).build();
        repo.save(row);
        row.setDeletedAt(OffsetDateTime.now());
        repo.save(row);
        assertThat(repo.findByImageUrlAndPodId("a", "b")).isEmpty();
    }

    @Test
    @Transactional
    void pruneBeforeDeletesStaleRows() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("b").status(ImageScanStatus.FAILED)
                .lastPulledAt(OffsetDateTime.now().minusMinutes(20)).build();
        repo.save(row);
        // @UpdateTimestamp stamps updatedAt = now on insert; force it
        // old so the row ages past the prune horizon.
        em.createQuery("update DockerImageState s set s.updatedAt = :old where s.id = :id")
                .setParameter("old", OffsetDateTime.now().minusMinutes(60))
                .setParameter("id", row.getId())
                .executeUpdate();
        int pruned = repo.pruneBefore(OffsetDateTime.now().minusMinutes(30),
                OffsetDateTime.now());
        assertThat(pruned).isGreaterThan(0);
        assertThat(repo.findByImageUrlAndPodId("a", "b")).isEmpty();
    }

    @Test
    @Transactional
    void pruneBeforeKeepsFreshRows() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("b").status(ImageScanStatus.FAILED)
                .lastPulledAt(OffsetDateTime.now()).build();
        repo.save(row);
        int pruned = repo.pruneBefore(OffsetDateTime.now().minusMinutes(30),
                OffsetDateTime.now());
        assertThat(pruned).isZero();
        assertThat(repo.findByImageUrlAndPodId("a", "b")).isPresent();
    }

    @Test
    void pruneBeforeManagesItsOwnTransaction() {
        // no @Transactional on this method — exercises the production path
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("tx").status(ImageScanStatus.FAILED).build();
        repo.save(row);                                   // commits on its own tx
        OffsetDateTime old = OffsetDateTime.now().minusMinutes(60);
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                em.createQuery("update DockerImageState s set s.updatedAt = :old where s.id = :id")
                        .setParameter("old", old)
                        .setParameter("id", row.getId())
                        .executeUpdate());
        assertThat(repo.pruneBefore(OffsetDateTime.now().minusMinutes(30),
                OffsetDateTime.now())).isGreaterThan(0);
        assertThat(repo.findByImageUrlAndPodId("a", "tx")).isEmpty();
    }
}

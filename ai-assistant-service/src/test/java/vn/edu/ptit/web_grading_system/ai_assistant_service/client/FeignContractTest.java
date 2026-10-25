package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "feign.course-service.url=http://localhost:8081",
        "feign.submission-service.url=http://localhost:8082",
        "feign.result-service.url=http://localhost:8083",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testfeign;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
public class FeignContractTest {

    @MockitoBean
    private SubmissionServiceClient submissionServiceClient;

    @MockitoBean
    private CourseServiceClient courseServiceClient;

    @Test
    void testFeignClientsConfigured() {
        assertNotNull(submissionServiceClient);
        assertNotNull(courseServiceClient);
    }
}

package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:testprops",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
public class AgentPropertiesTest {

    @Autowired
    private AgentProperties agentProperties;

    @Test
    void testPropertiesBoundSuccessfully() {
        assertNotNull(agentProperties, "AgentProperties should be auto-wired");
        assertEquals(8, agentProperties.getLimits().getMaxModelSteps());
        assertEquals(12, agentProperties.getLimits().getMaxToolCalls());
        assertEquals(120, agentProperties.getLimits().getActiveTimeoutSeconds());
        assertEquals(10, agentProperties.getContext().getHistoryWindowSize());
    }
}

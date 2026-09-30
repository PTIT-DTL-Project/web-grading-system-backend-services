package vn.edu.ptit.web_grading_system.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the route table from {@code spring.cloud.gateway.server.webflux.routes}
 * (Spring Cloud Gateway 5.x prefix) and asserts the three expected routes.
 *
 * <p>Replaces the deleted {@code TestRunner} scaffolding, whose second
 * {@code @SpringBootApplication} made the module fail with "Found multiple
 * @SpringBootConfiguration annotated classes" (Review: 2026-09-30, Pullfrog review
 * feat/DAT-8). The class name deliberately avoids the {@code *ApplicationTests} suffix:
 * CI runs {@code mvn test -Dtest='!*ApplicationTests'}, so only that shape gets a signal.
 */
@SpringBootTest
class GatewayRoutesTest {

	@Autowired
	private GatewayProperties gatewayProperties;

	@Test
	void bindsThreeRoutesFromWebfluxPrefix() {
		assertThat(gatewayProperties.getRoutes())
				.extracting("id")
				.containsExactlyInAnyOrder("course-service", "submission-service", "result-service");
	}
}

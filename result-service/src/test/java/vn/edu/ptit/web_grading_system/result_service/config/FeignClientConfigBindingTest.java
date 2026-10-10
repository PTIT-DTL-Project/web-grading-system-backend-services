package vn.edu.ptit.web_grading_system.result_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the Feign timeout binding to the real {@code application.yaml}: a
 * wrong property prefix binds silently to nothing (hit 2026-10-10 with the
 * legacy {@code feign.client.config} prefix on openfeign 5.x), leaving the
 * client on Feign's ~60s default read timeout. This test binds exactly like
 * Boot does and fails if the timeouts stop reaching the client config.
 */
class FeignClientConfigBindingTest {

    @Test
    void courseServiceTimeoutsBindFromApplicationYaml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();
        assertNotNull(properties, "application.yaml must be on the test classpath");

        FeignClientProperties bound = new Binder(ConfigurationPropertySources.from(
                        new org.springframework.core.env.PropertiesPropertySource("app", properties)))
                .bind("spring.cloud.openfeign.client", FeignClientProperties.class)
                .orElseThrow(() -> new AssertionError("prefix must bind"));

        assertTrue(bound.getConfig().containsKey("course-service"),
                "course-service client config must be present");
        assertEquals(2000, bound.getConfig().get("course-service").getConnectTimeout());
        assertEquals(3000, bound.getConfig().get("course-service").getReadTimeout());
    }
}

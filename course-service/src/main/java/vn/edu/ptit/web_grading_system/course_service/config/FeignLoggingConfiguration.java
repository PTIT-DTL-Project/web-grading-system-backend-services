package vn.edu.ptit.web_grading_system.course_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.Client;
import vn.edu.ptit.web_grading_system.course_service.service.HttpLogService;

// Per-client configuration: reference from @FeignClient(configuration = FeignLoggingConfiguration.class).
// Not a @Configuration — must not be picked up globally, or it would override the auto-configured Client.
public class FeignLoggingConfiguration {

    @Bean
    public Client loggingFeignClient(HttpLogService httpLogService,
            @Value("${spring.application.name}") String serviceName) {
        // NOTE: delegate must NOT be an injected Client parameter. This config runs in
        // each Feign child context, where this bean is the sole Client candidate, so an
        // injected Client self-resolves -> BeanCurrentlyInCreationException (startup cycle).
        // Client.Default is exactly what the framework would have supplied here
        // (no hc5/okhttp/loadbalancer Client customizations on the classpath).
        return new LoggingFeignClient(httpLogService, serviceName, new Client.Default(null, null));
    }
}

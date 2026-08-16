package vn.edu.ptit.web_grading_system.executor_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.Client;
import vn.edu.ptit.web_grading_system.executor_service.service.HttpLogService;

// Per-client configuration: reference from @FeignClient(configuration = FeignLoggingConfiguration.class).
// Not a @Configuration — must not be picked up globally, or it would override the auto-configured Client.
public class FeignLoggingConfiguration {

    @Bean
    public Client loggingFeignClient(Client delegate, HttpLogService httpLogService,
            @Value("${spring.application.name}") String serviceName) {
        return new LoggingFeignClient(httpLogService, serviceName, delegate);
    }
}
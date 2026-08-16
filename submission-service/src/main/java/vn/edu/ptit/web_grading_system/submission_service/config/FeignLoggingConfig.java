package vn.edu.ptit.web_grading_system.submission_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Client;
import vn.edu.ptit.web_grading_system.submission_service.service.HttpLogService;

@Configuration
public class FeignLoggingConfig {

    @Bean
    public Client loggingFeignClient(HttpLogService httpLogService,
            @Value("${spring.application.name}") String serviceName) {
        return new LoggingFeignClient(httpLogService, serviceName);
    }
}
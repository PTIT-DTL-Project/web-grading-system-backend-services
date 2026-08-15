package vn.edu.ptit.web_grading_system.api_gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableFeignClients
public class ApiGatewayApplication {

	private static final Logger log = LoggerFactory.getLogger(ApiGatewayApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@Bean
	public CommandLineRunner startupInfo() {
		return args -> log.info("🚀 API Gateway started successfully — version 4.4.0");
	}

}

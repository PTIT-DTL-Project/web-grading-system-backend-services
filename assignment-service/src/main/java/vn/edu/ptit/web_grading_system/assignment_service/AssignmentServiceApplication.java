package vn.edu.ptit.web_grading_system.assignment_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableFeignClients
public class AssignmentServiceApplication {

	private static final Logger log = LoggerFactory.getLogger(AssignmentServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(AssignmentServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner startupInfo() {
		return args -> log.info("🚀 Assignment Service started successfully!!!");
	}

}

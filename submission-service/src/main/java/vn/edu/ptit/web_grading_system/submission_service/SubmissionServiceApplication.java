package vn.edu.ptit.web_grading_system.submission_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class SubmissionServiceApplication {

	private static final Logger log = LoggerFactory.getLogger(SubmissionServiceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SubmissionServiceApplication.class, args);
	}

	@Bean
	public CommandLineRunner startupInfo() {
		return args -> log.info("Submission Service started successfully");
	}

}

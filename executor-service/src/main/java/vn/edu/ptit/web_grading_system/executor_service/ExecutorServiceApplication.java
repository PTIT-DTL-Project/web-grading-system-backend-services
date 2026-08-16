package vn.edu.ptit.web_grading_system.executor_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableFeignClients
public class ExecutorServiceApplication {


	public static void main(String[] args) {
		SpringApplication.run(ExecutorServiceApplication.class, args);
	}


}
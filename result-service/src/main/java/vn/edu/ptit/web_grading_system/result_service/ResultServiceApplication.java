package vn.edu.ptit.web_grading_system.result_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ResultServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResultServiceApplication.class, args);
	}

}

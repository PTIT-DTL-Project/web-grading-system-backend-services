package vn.edu.ptit.web_grading_system.executor_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@EnableScheduling
@EnableKafka
@ConfigurationPropertiesScan
@SpringBootApplication
@EnableFeignClients
public class ExecutorServiceApplication {

	@Bean(name = "gradingTaskExecutor")
	public Executor gradingTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(10);
		executor.setThreadNamePrefix("grading-");
		executor.initialize();
		return executor;
	}

	public static void main(String[] args) {
		SpringApplication.run(ExecutorServiceApplication.class, args);
	}
}
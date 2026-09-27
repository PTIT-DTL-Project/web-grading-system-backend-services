package vn.edu.ptit.web_grading_system.api_gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.config.GatewayProperties;

@SpringBootApplication
public class TestRunner implements CommandLineRunner {

    @Autowired
    private GatewayProperties properties;

    public static void main(String[] args) {
        SpringApplication.run(TestRunner.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("ROUTES_COUNT=" + properties.getRoutes().size());
        System.exit(0);
    }
}

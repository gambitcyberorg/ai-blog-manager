package org.gc.aiagents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "org.gc")
@EnableFeignClients
public class AiAgentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentsApplication.class, args);
    }

}

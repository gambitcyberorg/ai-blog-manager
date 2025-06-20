package org.gc.aiagents.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.azure.openai")
public class AzureOpenAIProperties {

    private Map<String, Client> clients;

    @Data
    public static class Client {
        private String apiKey;
        private String endpoint;
        private String deploymentName;
        private int maxConcurrentRequests = 1; // Default to 1 concurrent request per client
    }
} 
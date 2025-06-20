package org.gc.aiagents;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
public class AzureOpenAIConnectionTest {

    // Replace these with your actual Azure OpenAI credentials
    private static final String AZURE_OPENAI_ENDPOINT = "https://gptmitretestingnew.openai.azure.com/";
    private static final String AZURE_OPENAI_API_KEY = "YOUR_API_KEY_HERE"; // Replace with actual key
    private static final String DEPLOYMENT_NAME = "YOUR_DEPLOYMENT_NAME"; // Replace with actual deployment name

    @Test
    public void testAzureOpenAIConnection() {
        log.info("Testing Azure OpenAI connection...");
        
        try {
            // Create client with extended timeout and logging
            OpenAIClient client = new OpenAIClientBuilder()
                    .endpoint(AZURE_OPENAI_ENDPOINT)
                    .credential(new AzureKeyCredential(AZURE_OPENAI_API_KEY))
                    .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS))
                    .buildClient();

            log.info("Client created successfully");

            // Simple test message
            List<ChatRequestMessage> prompts = new ArrayList<>();
            prompts.add(new ChatRequestUserMessage("Hello, this is a connection test."));

            ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                    .setMaxTokens(50)
                    .setTemperature(0.1);

            log.info("Sending test request to Azure OpenAI...");
            
            // Time the request
            long startTime = System.currentTimeMillis();
            ChatCompletions response = client.getChatCompletions(DEPLOYMENT_NAME, options);
            long endTime = System.currentTimeMillis();
            
            log.info("Request completed in {} ms", endTime - startTime);

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String responseText = response.getChoices().get(0).getMessage().getContent();
                log.info("Azure OpenAI Response: {}", responseText);
                log.info("✅ Connection test PASSED");
            } else {
                log.error("❌ Empty response from Azure OpenAI");
            }

        } catch (Exception e) {
            log.error("❌ Azure OpenAI connection test FAILED: {}", e.getMessage(), e);
            
            // Additional diagnostic information
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
            
            // Check for specific error types
            if (e.getMessage().contains("Connection reset")) {
                log.error("🔍 Connection reset detected - possible network/firewall issue");
            } else if (e.getMessage().contains("timeout") || e.getMessage().contains("Timeout")) {
                log.error("🔍 Timeout detected - request taking too long");
            } else if (e.getMessage().contains("SSL") || e.getMessage().contains("certificate")) {
                log.error("🔍 SSL/Certificate issue detected");
            } else if (e.getMessage().contains("401") || e.getMessage().contains("Unauthorized")) {
                log.error("🔍 Authentication issue - check API key");
            } else if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
                log.error("🔍 Resource not found - check endpoint URL and deployment name");
            }
        }
    }

    @Test
    public void testNetworkConnectivity() {
        log.info("Testing basic network connectivity to Azure OpenAI endpoint...");
        
        try {
            java.net.URL url = new java.net.URL(AZURE_OPENAI_ENDPOINT);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            log.info("HTTP Response Code: {}", responseCode);
            
            if (responseCode == 401) {
                log.info("✅ Network connectivity is OK (401 expected without API key)");
            } else {
                log.info("Network test response code: {}", responseCode);
            }
            
        } catch (Exception e) {
            log.error("❌ Network connectivity test failed: {}", e.getMessage());
        }
    }

    @Test
    public void testDNSResolution() {
        log.info("Testing DNS resolution for Azure OpenAI endpoint...");
        
        try {
            java.net.URL url = new java.net.URL(AZURE_OPENAI_ENDPOINT);
            java.net.InetAddress address = java.net.InetAddress.getByName(url.getHost());
            log.info("✅ DNS Resolution successful: {} -> {}", url.getHost(), address.getHostAddress());
        } catch (Exception e) {
            log.error("❌ DNS Resolution failed: {}", e.getMessage());
        }
    }
} 
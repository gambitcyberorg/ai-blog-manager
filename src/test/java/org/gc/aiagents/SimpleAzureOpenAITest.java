package org.gc.aiagents;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import lombok.extern.slf4j.Slf4j;
import org.gc.aiagents.properties.AzureOpenAIProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")  // This will load application-local.yml
public class SimpleAzureOpenAITest {

    @Autowired
    private AzureOpenAIProperties azureOpenAIProperties;

    @Test
    public void testConnectionWithLocalConfig() {
        System.out.println("🔄 Testing Azure OpenAI connection using application-local.yml config...");
        
        if (azureOpenAIProperties.getClients() == null || azureOpenAIProperties.getClients().isEmpty()) {
            System.out.println("❌ No Azure OpenAI clients configured in application-local.yml");
            return;
        }

        // Test each configured client
        azureOpenAIProperties.getClients().forEach((clientName, clientConfig) -> {
            System.out.println("\n🔧 Testing client: " + clientName);
            System.out.println("📍 Endpoint: " + clientConfig.getEndpoint());
            System.out.println("🚀 Deployment: " + clientConfig.getDeploymentName());
            
            try {
                // Create client using config from application-local.yml
                OpenAIClient client = new OpenAIClientBuilder()
                        .endpoint(clientConfig.getEndpoint())
                        .credential(new AzureKeyCredential(clientConfig.getApiKey()))
                        .buildClient();

                // Simple test prompt
                List<ChatRequestMessage> messages = new ArrayList<>();
                messages.add(new ChatRequestSystemMessage("You are a helpful assistant."));
                messages.add(new ChatRequestUserMessage("Say hello and confirm you're working!"));

                ChatCompletionsOptions options = new ChatCompletionsOptions(messages)
                        .setMaxTokens(100)
                        .setTemperature(0.7);

                System.out.println("📤 Sending test request...");
                
                long startTime = System.currentTimeMillis();
                ChatCompletions response = client.getChatCompletions(clientConfig.getDeploymentName(), options);
                long endTime = System.currentTimeMillis();
                
                if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                    String responseText = response.getChoices().get(0).getMessage().getContent();
                    System.out.println("✅ SUCCESS for " + clientName + " (" + (endTime-startTime) + "ms):");
                    System.out.println("📝 Response: " + responseText);
                } else {
                    System.out.println("❌ FAILED: Got empty response for " + clientName);
                }

            } catch (Exception e) {
                System.out.println("❌ FAILED for " + clientName + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Test
    public void testIndicatorExtractionWithLocalConfig() {
        System.out.println("🔄 Testing indicator extraction with local config...");
        
        // Get the first available client
        AzureOpenAIProperties.Client clientConfig = azureOpenAIProperties.getClients().values().iterator().next();
        String clientName = azureOpenAIProperties.getClients().keySet().iterator().next();
        
        // Hardcoded test content similar to what PythonApiService processes
        String testContent = """
            Threat Report: BlackMatter Ransomware Analysis
            
            The BlackMatter ransomware group has been observed using the following indicators:
            - IP Address: 192.168.1.100 was used as a command and control server
            - Domain: evil-domain.com was registered for malicious activities
            - Hash: d41d8cd98f00b204e9800998ecf8427e (MD5) found in malicious file
            - Email: attacker@protonmail.com used for ransom communications
            - URL: http://evil-domain.com/payload.exe served the malware payload
            
            Report created on: 15-06-2024
            """;

        String indicatorPrompt = """
            You are an expert cybersecurity analyst. Extract cybersecurity indicators from the following content.
            Return the result in JSON format with the following structure:
            {
                "name": "Report Name",
                "description": "Brief description",
                "created": "DD-MM-YYYY",
                "indicators": [
                    {
                        "type": "ip",
                        "value": "192.168.1.100",
                        "description": "C2 server"
                    }
                ]
            }
            
            Content to analyze:
            """ + testContent;

        try {
            OpenAIClient client = new OpenAIClientBuilder()
                    .endpoint(clientConfig.getEndpoint())
                    .credential(new AzureKeyCredential(clientConfig.getApiKey()))
                    .buildClient();

            List<ChatRequestMessage> messages = new ArrayList<>();
            messages.add(new ChatRequestSystemMessage("You are an expert cybersecurity analyst."));
            messages.add(new ChatRequestUserMessage(indicatorPrompt));

            ChatCompletionsOptions options = new ChatCompletionsOptions(messages)
                    .setMaxTokens(2000)
                    .setTemperature(0.1);

            System.out.println("📤 Sending indicator extraction request to " + clientName + "...");
            
            long startTime = System.currentTimeMillis();
            ChatCompletions response = client.getChatCompletions(clientConfig.getDeploymentName(), options);
            long endTime = System.currentTimeMillis();
            
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String responseText = response.getChoices().get(0).getMessage().getContent();
                System.out.println("✅ SUCCESS! Indicator extraction (" + (endTime-startTime) + "ms):");
                System.out.println("📝 JSON Response: " + responseText);
            } else {
                System.out.println("❌ FAILED: Got empty response for indicator extraction");
            }

        } catch (Exception e) {
            System.out.println("❌ FAILED indicator extraction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testTechniqueExtractionWithLocalConfig() {
        System.out.println("🔄 Testing technique extraction with local config...");
        
        // Get the second client if available, otherwise use first
        AzureOpenAIProperties.Client clientConfig;
        String clientName;
        
        if (azureOpenAIProperties.getClients().size() > 1) {
            clientName = azureOpenAIProperties.getClients().keySet().stream().skip(1).findFirst().orElse("");
            clientConfig = azureOpenAIProperties.getClients().get(clientName);
        } else {
            clientName = azureOpenAIProperties.getClients().keySet().iterator().next();
            clientConfig = azureOpenAIProperties.getClients().values().iterator().next();
        }
        
        String testContent = """
            The attackers used spear-phishing emails to gain initial access (T1566.001).
            After gaining access, they performed credential dumping using Mimikatz (T1003.001).
            Lateral movement was achieved through SMB connections (T1021.002).
            They established persistence by creating scheduled tasks (T1053.005).
            Data was exfiltrated over C2 channel (T1041).
            """;

        String techniquePrompt = """
            Extract MITRE ATT&CK techniques from the following text.
            Return in JSON format:
            {
                "techniques": [
                    {
                        "technique_id": "T1566.001",
                        "technique_name": "Spearphishing Attachment",
                        "technique_usage": "Used spear-phishing emails for initial access"
                    }
                ]
            }
            
            Content: """ + testContent;

        try {
            OpenAIClient client = new OpenAIClientBuilder()
                    .endpoint(clientConfig.getEndpoint())
                    .credential(new AzureKeyCredential(clientConfig.getApiKey()))
                    .buildClient();

            List<ChatRequestMessage> messages = new ArrayList<>();
            messages.add(new ChatRequestSystemMessage("You are a MITRE ATT&CK expert."));
            messages.add(new ChatRequestUserMessage(techniquePrompt));

            ChatCompletionsOptions options = new ChatCompletionsOptions(messages)
                    .setMaxTokens(1500)
                    .setTemperature(0.1);

            System.out.println("📤 Sending technique extraction request to " + clientName + "...");
            
            long startTime = System.currentTimeMillis();
            ChatCompletions response = client.getChatCompletions(clientConfig.getDeploymentName(), options);
            long endTime = System.currentTimeMillis();
            
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String responseText = response.getChoices().get(0).getMessage().getContent();
                System.out.println("✅ SUCCESS! Technique extraction (" + (endTime-startTime) + "ms):");
                System.out.println("📝 JSON Response: " + responseText);
            } else {
                System.out.println("❌ FAILED: Got empty response for technique extraction");
            }

        } catch (Exception e) {
            System.out.println("❌ FAILED technique extraction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testBothClientsStressTest() {
        System.out.println("🔄 Testing both clients with multiple requests (like PythonApiService does)...");
        
        String[] prompts = {
            "What is cybersecurity?",
            "List 3 types of malware",
            "What is a firewall?",
            "Explain phishing attacks",
            "What is MITRE ATT&CK?"
        };

        azureOpenAIProperties.getClients().forEach((clientName, clientConfig) -> {
            System.out.println("\n🧪 Testing client: " + clientName);
            
            try {
                OpenAIClient client = new OpenAIClientBuilder()
                        .endpoint(clientConfig.getEndpoint())
                        .credential(new AzureKeyCredential(clientConfig.getApiKey()))
                        .buildClient();

                int successCount = 0;
                
                for (int i = 0; i < prompts.length; i++) {
                    try {
                        System.out.println("📤 Request " + (i+1) + "/5 to " + clientName + ": " + prompts[i]);
                        
                        List<ChatRequestMessage> messages = new ArrayList<>();
                        messages.add(new ChatRequestSystemMessage("You are a cybersecurity expert. Give short answers."));
                        messages.add(new ChatRequestUserMessage(prompts[i]));

                        ChatCompletionsOptions options = new ChatCompletionsOptions(messages)
                                .setMaxTokens(100)
                                .setTemperature(0.1);
                        
                        long startTime = System.currentTimeMillis();
                        ChatCompletions response = client.getChatCompletions(clientConfig.getDeploymentName(), options);
                        long endTime = System.currentTimeMillis();
                        
                        if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                            String responseText = response.getChoices().get(0).getMessage().getContent();
                            System.out.println("✅ Response " + (i+1) + " (" + (endTime-startTime) + "ms): " + 
                                             responseText.substring(0, Math.min(responseText.length(), 100)) + "...");
                            successCount++;
                        } else {
                            System.out.println("❌ Empty response for request " + (i+1));
                        }
                        
                        // Small delay between requests
                        Thread.sleep(500);
                        
                    } catch (Exception e) {
                        System.out.println("❌ Request " + (i+1) + " failed: " + e.getMessage());
                    }
                }
                
                System.out.println("📊 Client " + clientName + " Summary: " + successCount + "/" + prompts.length + " requests succeeded");
                
            } catch (Exception e) {
                System.out.println("❌ Failed to create client " + clientName + ": " + e.getMessage());
            }
        });
    }

    @Test
    public void printConfigInfo() {
        System.out.println("🔧 Current Azure OpenAI Configuration from application-local.yml:");
        
        if (azureOpenAIProperties.getClients() != null) {
            azureOpenAIProperties.getClients().forEach((name, config) -> {
                System.out.println("\n📋 Client: " + name);
                System.out.println("  🌐 Endpoint: " + config.getEndpoint());
                System.out.println("  🚀 Deployment: " + config.getDeploymentName());
                System.out.println("  🔑 API Key: " + (config.getApiKey() != null ? 
                    config.getApiKey().substring(0, 10) + "..." : "NOT SET"));
            });
        } else {
            System.out.println("❌ No clients configured!");
        }
    }
} 
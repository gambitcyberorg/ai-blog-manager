package org.gc.aiagents.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.http.policy.TimeoutPolicy;
import lombok.extern.slf4j.Slf4j;
import org.gc.aiagents.properties.AzureOpenAIProperties;
import org.springframework.stereotype.Service;
import com.azure.ai.openai.OpenAIAsyncClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class AzureOpenAIService {

    private final Map<String, OpenAIClient> clients = new HashMap<>();
    private final Map<String, OpenAIAsyncClient> asyncClients = new HashMap<>();
    private final Map<String, String> deploymentNames = new HashMap<>();
    private final Map<String, Semaphore> clientSemaphores = new HashMap<>();
    private final AzureOpenAIProperties properties;

    public AzureOpenAIService(AzureOpenAIProperties properties) {
        this.properties = properties;
        if (properties.getClients() == null || properties.getClients().isEmpty()) {
            log.warn("No Azure OpenAI clients configured. AzureOpenAIService will be unavailable.");
            return;
        }
        properties.getClients().forEach((name, clientProps) -> {
            // Enhanced client with timeout and retry policies
            OpenAIClient client = new OpenAIClientBuilder()
                    .endpoint(clientProps.getEndpoint())
                    .credential(new AzureKeyCredential(clientProps.getApiKey()))
                    .addPolicy(new TimeoutPolicy(Duration.ofSeconds(120))) // 2 minute timeout
                    .addPolicy(new RetryPolicy()) // Default retry policy
                    .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BASIC))
                    .buildClient();
            clients.put(name, client);

            OpenAIAsyncClient asyncClient = new OpenAIClientBuilder()
                    .endpoint(clientProps.getEndpoint())
                    .credential(new AzureKeyCredential(clientProps.getApiKey()))
                    .addPolicy(new TimeoutPolicy(Duration.ofSeconds(120))) // 2 minute timeout
                    .addPolicy(new RetryPolicy()) // Default retry policy
                    .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BASIC))
                    .buildAsyncClient();
            asyncClients.put(name, asyncClient);

            deploymentNames.put(name, clientProps.getDeploymentName());
            
            // Create semaphore with configurable permits per client for concurrency control
            int maxConcurrent = clientProps.getMaxConcurrentRequests();
            clientSemaphores.put(name, new Semaphore(maxConcurrent));
            
            log.info("Initialized Azure OpenAI client '{}' with endpoint: {} (max concurrent: {})", 
                    name, clientProps.getEndpoint(), maxConcurrent);
        });
    }

    public Mono<String> getChatCompletionAsync(String clientName, String systemPrompt, String userPrompt) {
        OpenAIAsyncClient client = asyncClients.get(clientName);
        String deploymentName = deploymentNames.get(clientName);
        Semaphore semaphore = clientSemaphores.get(clientName);

        if (client == null || deploymentName == null || semaphore == null) {
            log.error("No async client, deployment name, or semaphore found for '{}'", clientName);
            return Mono.error(new IllegalArgumentException("Invalid client name: " + clientName));
        }

        List<ChatRequestMessage> prompts = new ArrayList<>();
        prompts.add(new ChatRequestSystemMessage(systemPrompt));
        prompts.add(new ChatRequestUserMessage(userPrompt));

        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(8000)
                .setTemperature(0.7)
                .setTopP(0.95);

        // Use semaphore to control per-client concurrency
        return Mono.fromCallable(() -> {
                    log.debug("Acquiring semaphore for client '{}' (available permits: {})", clientName, semaphore.availablePermits());
                    semaphore.acquire();
                    log.debug("Acquired semaphore for client '{}'", clientName);
                    return true;
                })
                .flatMap(acquired -> 
                    client.getChatCompletions(deploymentName, options)
                            .timeout(Duration.ofSeconds(120)) // Additional timeout at Reactor level
                            .map(chatCompletions -> chatCompletions.getChoices().stream()
                                    .map(ChatChoice::getMessage)
                                    .map(message -> Objects.toString(message.getContent(), null))
                                    .filter(Objects::nonNull)
                                    .findFirst()
                                    .orElse(null))
                            .doOnError(e -> log.error("Error getting async chat completion from Azure OpenAI for client '{}': {}", clientName, e.getMessage()))
                            .doFinally(signalType -> {
                                semaphore.release();
                                log.debug("Released semaphore for client '{}' (signal: {})", clientName, signalType);
                            })
                            .onErrorResume(e -> {
                                log.warn("Retrying after error for client '{}': {}", clientName, e.getMessage());
                                return Mono.empty(); // Return empty to trigger fallback logic
                            })
                );
    }

    public String getChatCompletion(String clientName, String systemPrompt, String userPrompt) {
        try {
            OpenAIClient client = clients.get(clientName);
            String deploymentName = deploymentNames.get(clientName);

            if (client == null || deploymentName == null) {
                log.error("No client or deployment name found for '{}'", clientName);
                return null;
            }

            List<ChatRequestMessage> prompts = new ArrayList<>();
            prompts.add(new ChatRequestSystemMessage(systemPrompt));
            prompts.add(new ChatRequestUserMessage(userPrompt));

            ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                    .setMaxTokens(8000)
                    .setTemperature(0.7)
                    .setTopP(0.95);

            log.debug("Sending chat completion request to client '{}' with deployment '{}'", clientName, deploymentName);
            ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);

            String result = chatCompletions.getChoices().stream()
                    .map(ChatChoice::getMessage)
                    .map(message -> Objects.toString(message.getContent(), null))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            
            log.debug("Successfully received response from client '{}', response length: {}", 
                     clientName, result != null ? result.length() : 0);
            return result;

        } catch (Exception e) {
            log.error("Error getting chat completion from Azure OpenAI for client '{}': {}", clientName, e.getMessage(), e);
            return null;
        }
    }
    
    public Map<String, String> getAvailableClients() {
        Map<String, String> availableClients = new HashMap<>();
        deploymentNames.forEach((name, deployment) -> {
            availableClients.put(name, deployment);
        });
        return availableClients;
    }
} 
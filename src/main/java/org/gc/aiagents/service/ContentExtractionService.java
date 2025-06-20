package org.gc.aiagents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentExtractionService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${firecrawl.url:https://api.firecrawl.dev/v0}")
    private String firecrawlUrl;
    
    @Value("${firecrawl.api.key:}")
    private String firecrawlApiKey;
    
    public Mono<ContentExtractionResult> extractContentFromUrl(String url) {
        if (!url.startsWith("http")) {
            return Mono.error(new IllegalArgumentException("URL must start with 'http'"));
        }
        
        if (url.toLowerCase().endsWith(".pdf")) {
            // For PDFs, we'll delegate to Python service
            return Mono.just(ContentExtractionResult.builder()
                    .filteredContent("PDF_CONTENT_PLACEHOLDER")
                    .rawContent("PDF_CONTENT_PLACEHOLDER")
                    .metadata(Map.of("url", url, "type", "pdf"))
                    .build());
        } else {
            return extractWebContent(url);
        }
    }
    
    private Mono<ContentExtractionResult> extractWebContent(String url) {
        // First try with Firecrawl if API key is available
        if (firecrawlApiKey != null && !firecrawlApiKey.trim().isEmpty() && !firecrawlApiKey.equals("Bearer ")) {
            return extractWithFirecrawl(url)
                    .onErrorResume(throwable -> {
                        log.warn("Firecrawl extraction failed for {}, falling back to Jsoup: {}", url, throwable.getMessage());
                        return extractWithJsoup(url);
                    });
        } else {
            return extractWithJsoup(url);
        }
    }
    
    private Mono<ContentExtractionResult> extractWithFirecrawl(String url) {
        WebClient webClient = webClientBuilder
                .baseUrl(firecrawlUrl)
                .defaultHeader("Authorization", firecrawlApiKey)
                .build();
        
        Map<String, Object> requestBody = Map.of(
                "url", url,
                "formats", new String[]{"markdown", "html"}
        );
        
        return webClient.post()
                .uri("/scrape")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .map(response -> {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null) {
                        String markdown = (String) data.get("markdown");
                        String html = (String) data.get("html");
                        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
                        
                        return ContentExtractionResult.builder()
                                .filteredContent(markdown != null ? markdown : "")
                                .rawContent(html != null ? html : "")
                                .metadata(metadata != null ? metadata : new HashMap<>())
                                .build();
                    }
                    return ContentExtractionResult.builder()
                            .filteredContent("")
                            .rawContent("")
                            .metadata(new HashMap<>())
                            .build();
                });
    }
    
    private Mono<ContentExtractionResult> extractWithJsoup(String url) {
        return Mono.fromCallable(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(30000)
                        .get();
                
                // Extract text content
                String filteredContent = doc.body().text();
                String rawContent = doc.html();
                
                // Extract metadata
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("title", doc.title());
                metadata.put("url", url);
                
                // Extract meta tags
                doc.select("meta").forEach(meta -> {
                    String name = meta.attr("name");
                    String property = meta.attr("property");
                    String content = meta.attr("content");
                    
                    if (!name.isEmpty() && !content.isEmpty()) {
                        metadata.put("meta_" + name, content);
                    }
                    if (!property.isEmpty() && !content.isEmpty()) {
                        metadata.put("meta_" + property, content);
                    }
                });
                
                return ContentExtractionResult.builder()
                        .filteredContent(filteredContent)
                        .rawContent(rawContent)
                        .metadata(metadata)
                        .build();
                        
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract content from URL: " + url, e);
            }
        });
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContentExtractionResult {
        private String filteredContent;
        private String rawContent;
        private Map<String, Object> metadata;
    }
} 
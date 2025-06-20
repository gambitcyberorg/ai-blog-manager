package org.gc.aiagents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCollectionService {
    
    private final AzureOpenAIService azureOpenAIService;
    
    private static final int MAX_PAGES = 1;
    private static final int MAX_URLS = 10;
    private static final int MAX_URLS_PER_LLM_CALL = 25;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    
    private static final Set<String> EXCLUDED_KEYWORDS = Set.of(
            "login", "signup", "admin", "tag", "category", "about", "contact", 
            "privacy", "terms", "search", "archive", "rss", "feed"
    );
    
    public List<String> collectContentUrls(String baseUrl) {
        return collectContentUrls(baseUrl, MAX_PAGES, MAX_URLS);
    }
    
    public List<String> collectContentUrls(String baseUrl, int maxPages, int maxUrls) {
        log.info("Starting URL collection for: {}", baseUrl);
        
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String domain = extractDomain(normalizedBaseUrl);
        
        Set<String> collectedUrls = new HashSet<>();
        Queue<String> urlsToVisit = new LinkedList<>();
        Set<String> visitedUrls = new HashSet<>();
        
        urlsToVisit.add(normalizedBaseUrl);
        
        try {
            while (!urlsToVisit.isEmpty() && visitedUrls.size() < maxPages && collectedUrls.size() < maxUrls) {
                String currentUrl = urlsToVisit.poll();
                if (visitedUrls.contains(currentUrl)) {
                    continue;
                }
                
                // Rate limiting
                Thread.sleep(1000);
                
                try {
                    Document doc = Jsoup.connect(currentUrl)
                            .userAgent(USER_AGENT)
                            .timeout(30000)
                            .get();
                    
                    Elements links = doc.select("a[href]");
                    for (Element link : links) {
                        String href = link.attr("href");
                        if (href.contains("#")) {
                            href = href.split("#")[0];
                        }
                        
                        String fullUrl = resolveUrl(normalizedBaseUrl, href);
                        if (fullUrl != null && isValidContentUrl(fullUrl, normalizedBaseUrl, visitedUrls, collectedUrls)) {
                            collectedUrls.add(fullUrl);
                            urlsToVisit.add(fullUrl);
                        }
                    }
                    
                    visitedUrls.add(currentUrl);
                    
                } catch (IOException e) {
                    log.warn("Failed to fetch URL: {}, error: {}", currentUrl, e.getMessage());
                }
            }
            
            // Filter content-like URLs
            List<String> contentUrls = filterContentUrls(collectedUrls, normalizedBaseUrl);
            log.info("Collected {} content URLs from {}", contentUrls.size(), baseUrl);
            
            return contentUrls.stream().limit(maxUrls).collect(Collectors.toList());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("URL collection interrupted", e);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error collecting URLs from {}: {}", baseUrl, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<String> identifyBlogLinksWithLlm(List<String> candidateUrls, String parentUrl) {
        if (candidateUrls.isEmpty()) {
            return new ArrayList<>();
        }
        
        log.info("Identifying blog links using LLM for {} candidates from {}", candidateUrls.size(), parentUrl);
        
        Set<String> allIdentifiedBlogLinks = new HashSet<>();
        
        // Process URLs in chunks
        for (int i = 0; i < candidateUrls.size(); i += MAX_URLS_PER_LLM_CALL) {
            int endIndex = Math.min(i + MAX_URLS_PER_LLM_CALL, candidateUrls.size());
            List<String> chunk = candidateUrls.subList(i, endIndex);
            
            try {
                List<String> identifiedChunk = callLlmForBlogIdentification(chunk, parentUrl);
                allIdentifiedBlogLinks.addAll(identifiedChunk);
                
                // Rate limiting for LLM calls
                Thread.sleep(2000);
                
            } catch (Exception e) {
                log.error("Error calling LLM for blog identification: {}", e.getMessage());
            }
        }
        
        log.info("LLM identified {} unique blog links for {}", allIdentifiedBlogLinks.size(), parentUrl);
        
        return new ArrayList<>(allIdentifiedBlogLinks);
    }
    
    private List<String> callLlmForBlogIdentification(List<String> candidateUrls, String parentUrl) {
        String systemPrompt = """
            You are an expert at web content analysis. Given a base URL and a list of URLs found on that domain, 
            your task is to identify which of these URLs are likely to be blog posts or articles. 
            Exclude links to category pages, tags, author pages, contact, about, login, or main navigation pages. 
            Focus on URLs that represent individual content pieces. 
            Return your answer as a JSON list of strings, where each string is a blog/article URL. 
            If no blog/article URLs are found in the provided list, return an empty list [].
            Your response should be a JSON list of strings, where each string is a blog/article URL.
            Example response: 
            {
                "blog_article_urls": [
                    "https://thedfirreport.com/2024/03/04/threat-brief-wordpress-exploit-leads-to-godzilla-web-shell-discovery-new-cve",
                    "https://thedfirreport.com/2024/04/01/from-onenote-to-ransomnote-an-ice-cold-intrusion"
                ]
            }
            """;
        
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("Base URL: ").append(parentUrl).append("\n\n");
        userPromptBuilder.append("Candidate URLs:\n");
        for (String url : candidateUrls) {
            userPromptBuilder.append("- ").append(url).append("\n");
        }
        
        try {
            String response = azureOpenAIService.getChatCompletion("azure-1", systemPrompt, userPromptBuilder.toString());
            
            return parseLlmResponse(response);
            
        } catch (Exception e) {
            log.error("Error calling LLM for blog identification: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private List<String> parseLlmResponse(String response) {
        try {
            // Extract JSON from response (handle code blocks)
            String jsonStr = response;
            if (response.contains("```json")) {
                jsonStr = response.split("```json")[1].split("```")[0].trim();
            } else if (response.contains("```")) {
                jsonStr = response.split("```")[1].split("```")[0].trim();
            }
            
            // Simple JSON parsing for the expected format
            if (jsonStr.contains("blog_article_urls")) {
                // Extract URLs from the JSON structure
                String urlsSection = jsonStr.substring(jsonStr.indexOf("["), jsonStr.lastIndexOf("]") + 1);
                return parseUrlList(urlsSection);
            } else if (jsonStr.trim().startsWith("[")) {
                return parseUrlList(jsonStr);
            }
            
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private List<String> parseUrlList(String urlListJson) {
        List<String> urls = new ArrayList<>();
        try {
            // Simple parsing for JSON array of strings
            String content = urlListJson.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
                String[] parts = content.split(",");
                for (String part : parts) {
                    String url = part.trim().replaceAll("\"", "");
                    if (url.startsWith("http")) {
                        urls.add(url);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing URL list: {}", e.getMessage());
        }
        return urls;
    }
    
    private String normalizeBaseUrl(String baseUrl) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl;
    }
    
    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (URISyntaxException e) {
            log.error("Invalid URL: {}", url);
            return "";
        }
    }
    
    private String resolveUrl(String baseUrl, String href) {
        try {
            if (href.startsWith("http")) {
                return href;
            } else if (href.startsWith("/")) {
                URI baseUri = new URI(baseUrl);
                return baseUri.getScheme() + "://" + baseUri.getHost() + href;
            } else {
                URI baseUri = new URI(baseUrl);
                return baseUri.resolve(href).toString();
            }
        } catch (URISyntaxException e) {
            return null;
        }
    }
    
    private boolean isValidContentUrl(String fullUrl, String baseUrl, Set<String> visitedUrls, Set<String> collectedUrls) {
        return fullUrl.startsWith(baseUrl) &&
               !visitedUrls.contains(fullUrl) &&
               !collectedUrls.contains(fullUrl) &&
               !containsExcludedKeywords(fullUrl);
    }
    
    private boolean containsExcludedKeywords(String url) {
        String lowerUrl = url.toLowerCase();
        return EXCLUDED_KEYWORDS.stream().anyMatch(lowerUrl::contains);
    }
    
    private List<String> filterContentUrls(Set<String> collectedUrls, String baseUrl) {
        return collectedUrls.stream()
                .filter(url -> {
                    String path = url.substring(baseUrl.length()).replaceAll("/$", "");
                    String[] components = path.split("/");
                    return (components.length >= 2 || path.endsWith(".html")) && !path.isEmpty();
                })
                .collect(Collectors.toList());
    }
} 
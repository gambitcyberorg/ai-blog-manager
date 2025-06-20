package org.gc.aiagents.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogContentResponse {
    
    private String articleUrl;
    private String filteredContent;
    private String rawContent;
    private Map<String, Object> metadata;
    private Map<String, Object> extractedIntel;
    private boolean success;
    private String errorMessage;
    
    public static BlogContentResponse success(String articleUrl, String filteredContent, 
                                            String rawContent, Map<String, Object> metadata,
                                            Map<String, Object> extractedIntel) {
        return BlogContentResponse.builder()
                .articleUrl(articleUrl)
                .filteredContent(filteredContent)
                .rawContent(rawContent)
                .metadata(metadata)
                .extractedIntel(extractedIntel)
                .success(true)
                .build();
    }
    
    public static BlogContentResponse failure(String articleUrl, String errorMessage) {
        return BlogContentResponse.builder()
                .articleUrl(articleUrl)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
} 
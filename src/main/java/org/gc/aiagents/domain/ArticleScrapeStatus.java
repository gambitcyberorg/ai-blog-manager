package org.gc.aiagents.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "article_scrape_status")
public class ArticleScrapeStatus {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String articleUid;
    
    @Field(type = FieldType.Keyword)
    private String articleUrl;
    
    @Field(type = FieldType.Keyword)
    private String parentUid;
    
    @Field(type = FieldType.Date)
    private Instant discoveryDatetimeUtc;
    
    @Field(type = FieldType.Keyword)
    private ScrapeStatus scrapeStatus;
    
    @Field(type = FieldType.Date)
    private Instant lastScrapeAttemptUtc;
    
    @Field(type = FieldType.Date)
    private Instant lastSuccessDatetimeUtc;
    
    @Field(type = FieldType.Integer)
    private Integer failureCount;
    
    @Field(type = FieldType.Date)
    private Instant retryAfterUtc;
    
    @Field(type = FieldType.Object)
    private Map<String, Object> externalApiResponse;
    
    @Field(type = FieldType.Date)
    private Instant createdAtUtc;
    
    @Field(type = FieldType.Date)
    private Instant updatedAtUtc;
    
    public enum ScrapeStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        MAX_FAILURES
    }
    
    public static ArticleScrapeStatus create(String articleUrl, String parentUid) {
        Instant now = Instant.now();
        String articleUid = UUID.randomUUID().toString();
        
        return ArticleScrapeStatus.builder()
                .id(articleUid)
                .articleUid(articleUid)
                .articleUrl(articleUrl)
                .parentUid(parentUid)
                .discoveryDatetimeUtc(now)
                .scrapeStatus(ScrapeStatus.PENDING)
                .failureCount(0)
                .createdAtUtc(now)
                .updatedAtUtc(now)
                .build();
    }
} 
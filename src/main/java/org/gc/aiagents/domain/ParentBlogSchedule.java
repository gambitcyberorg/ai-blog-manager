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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "parent_blog_schedules")
public class ParentBlogSchedule {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String parentUid;
    
    @Field(type = FieldType.Keyword)
    private String parentUrl;
    
    @Field(type = FieldType.Date)
    private Instant nextScanDatetimeUtc;
    
    @Field(type = FieldType.Integer)
    private Integer scanIntervalHours;
    
    @Field(type = FieldType.Date)
    private Instant lastScanAttemptUtc;
    
    @Field(type = FieldType.Date)
    private Instant lastScanSuccessUtc;
    
    @Field(type = FieldType.Date)
    private Instant createdAtUtc;
    
    @Field(type = FieldType.Date)
    private Instant updatedAtUtc;
    
    public static ParentBlogSchedule create(String parentUrl, Integer scanIntervalHours) {
        Instant now = Instant.now();
        String parentUid = UUID.randomUUID().toString();
        
        return ParentBlogSchedule.builder()
                .id(parentUid)
                .parentUid(parentUid)
                .parentUrl(parentUrl)
                .nextScanDatetimeUtc(now) // Start scanning immediately
                .scanIntervalHours(scanIntervalHours != null ? scanIntervalHours : 24 * 7) // Default weekly
                .createdAtUtc(now)
                .updatedAtUtc(now)
                .build();
    }
} 
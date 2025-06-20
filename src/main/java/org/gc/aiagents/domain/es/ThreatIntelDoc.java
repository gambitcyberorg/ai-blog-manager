package org.gc.aiagents.domain.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

@Data
@Document(indexName = "threat-intel")
public class ThreatIntelDoc {

    @Id
    private String id;

    @Field(type = FieldType.Date, name = "@timestamp")
    private Instant timestamp;

    @Field(type = FieldType.Text, name = "report_url")
    private String reportUrl;
    
    @Field(type = FieldType.Object, enabled = false)
    private Map<String, Object> intel;

} 
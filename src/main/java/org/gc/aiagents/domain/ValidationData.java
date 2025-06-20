package org.gc.aiagents.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import java.util.List;

@Data
@Document(indexName = "cti-analyst-mitre-validation-data")
public class ValidationData {
    @Id
    private String id;
    @JsonProperty("messages")
    private List<Message> messages;

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    public String toJsonLine() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
} 
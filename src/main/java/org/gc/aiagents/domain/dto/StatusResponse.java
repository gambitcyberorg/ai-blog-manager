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
public class StatusResponse {
    
    private String message;
    private Map<String, Object> details;
    
    public static StatusResponse success(String message) {
        return StatusResponse.builder()
                .message(message)
                .build();
    }
    
    public static StatusResponse success(String message, Map<String, Object> details) {
        return StatusResponse.builder()
                .message(message)
                .details(details)
                .build();
    }
} 
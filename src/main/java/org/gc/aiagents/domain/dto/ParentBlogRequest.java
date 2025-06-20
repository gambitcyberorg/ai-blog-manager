package org.gc.aiagents.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ParentBlogRequest {
    
    @NotBlank(message = "Parent URL is required")
    @URL(message = "Parent URL must be a valid URL")
    private String parentUrl;
    
    @Min(value = 1, message = "Scan interval must be at least 1 hour")
    private Integer scanIntervalHours;
} 
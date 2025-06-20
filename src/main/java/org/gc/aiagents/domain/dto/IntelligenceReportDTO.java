package org.gc.aiagents.domain.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class IntelligenceReportDTO {

    private String name;
    private String description;
    private List<Indicator> indicators;
    private List<Technique> techniques;

    @Data
    public static class Indicator {
        private String type;
        private String value;
        private String description;
        private String file;
        private String md5;
        private String sha1;
        private String sha256;
    }

    @Data
    public static class Technique {
        private String technique_id;
        private String technique_name;
        private Map<String, String> technique_usage;
    }
} 
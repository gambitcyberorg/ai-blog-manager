package org.gc.aiagents.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gambitcyber.datamodel.common.IndicatorType;
import lombok.Data;

import java.util.List;

@Data
public class IndicatorData {

    private String name;
    private String description;
    private String created;
    private List<Indicator> indicators;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Indicator {
        private IndicatorType type;
        private String value; // optional for non-hash types
        private String file;
        private String md5;
        private String sha1;
        private String sha256;
    }
}

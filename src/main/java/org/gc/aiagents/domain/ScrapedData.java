package org.gc.aiagents.domain;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class ScrapedData {

    private String data;

    private String source;

    private Boolean isOpenSource = false;

    private String urlToScrape;

    private LinkedHashMap<String , String> metadata;

    private boolean autoSave = false;
}

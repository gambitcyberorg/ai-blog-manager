package org.gc.aiagents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gc.aiagents.domain.ScrapedData;
import com.gambitcyber.datamodel.ui.GenericEntityDTO;
import com.gambitcyber.datamodel.ui.TechniqueDTO;
import com.gambitcyber.datamodel.ui.IndicatorDTO; // Kept for placeholder, primary indicator source is GraphData
import com.gambitcyber.datamodel.common.ReportSummary;
import com.gambitcyber.datamodel.common.EntityUsage;
import com.gambitcyber.datamodel.common.GraphData;
import com.gambitcyber.datamodel.common.IndicatorType;
import org.gc.aiagents.service.AzureOpenAIService;
import org.gc.aiagents.domain.es.ThreatIntelDoc;
import org.gc.aiagents.repository.es.ThreatIntelRepository;
import java.util.Objects;
import java.lang.StringBuilder;
import java.util.HashMap;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Set;
import java.util.HashSet;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.gc.aiagents.domain.dto.IntelligenceReportDTO;
import reactor.core.publisher.Flux;
import java.util.LinkedHashSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonApiService {
    
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AzureOpenAIService azureOpenAIService;
    private final ThreatIntelRepository threatIntelRepository;
    
    // @Value("${python.api.base-url:http://localhost:8000}")
    @Value("${python.api.base-url:https://knightguard-api-dev.gambitcyber.org}")
    private String pythonApiBaseUrl;
    
    @Value("${python.api.timeout.seconds:60}")
    private int timeoutSeconds;

    @Value("${python.api.max-concurrent-llm-calls:1}")
    private int maxConcurrentLlmCalls;
    
    // --- Prompts copied from CTIAnalystAIAgent ---
    private static final String EXTRACT_INDICATORS_PROMPT = "You are an expert cybersecurity analyst assisting users in extracting and analyzing relevant cybersecurity threats, tactics, techniques, and mitigations from structured content. Your goal is to summarize, extract key insights, and format the information in an actionable way.\n" +
            "\n" +
            "# Rules for Extraction\n" +
            "\n" +
            "- **Preserve Data Integrity**: Extract text exactly as it appears in the report without any modification.\n" +
            "- **Consistent Date Format**: It is the date when blog posted extract it from the content. The `created` field must always be in `DD-MM-YYYY` format. If the date is missing, the field should be returned as an empty string `\"\"`.\n" +
            "- **Indicators Handling**:\n" +
            "  - **ip**: Extract valid IPv4 addresses.\n" +
            "  - **domain**: Extract domain names (without subdirectories or protocols).\n" +
            "  - **url**: Extract full URLs, ensuring proper formatting.\n" +
            "  - **hash**: Extract cryptographic hashes as they appear in the text.\n" +
            "  - **email**: Extract emailIds mentioned in the content, ensuring proper formatting. \n" +
            " Once the data is extracted, make sure you filter out all the domains and emails that belong to whitelisted organisations like CIA, Microsoft, Crowdstrike, trend micro etc.\n" +
            "It is very important to remove any whitelisted domains and emails from the response since they do not constitute an indicator of compromise \n" +
            "- **Missing Data Handling**:\n" +
            "  - If a specific field (e.g., `name`, `description`, `created`) is missing, return it as an empty string `\"\"`.\n" +
            "  - For the `indicators` field, if no indicators are present in the report, return an empty list `[]`.\n" +
            "- **Output Format Only**: The returned output must strictly adhere to the specified JSON structure without additional information, comments, or explanations.\n" +
            "\n" +
            "# Output Format\n" +
            "\n" +
            "The output must follow this JSON structure:\n" +
            "```json\n" +
            "{\n" +
            "\t\"name\": \"<Name of the Report>\",\n" +
            "\t\"description\": \"<Description of the Report>\",\n" +
            "\t\"created\": \"<yyyy-MM-dd HH:mm:ss>\",\n" +
            "\t\"indicators\": [\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"<ip/domain/url/email>\",\n" +
            "\t\t\t\"value\": \"<extracted value>\"\n" +
            "\t\t\t\"description\": \"small description of how this indicator was used.\"\n" +
            "\t\t}\n" +
            "        {\n" +
            "            \"type\": \"<hash>\",\n" +
            "            \"file\" :\"<filename>\"\n" +
            "            \"md5\": \"438448FDC7521ED034F6DABDF814B6BA\"\n" +
            "            \"sha1\": \"F08E7343A94897ADEAE78138CC3F9142ED160A03\"\n" +
            "            \"sha256\":c\"1E2E25A996F72089F12755F931E7FCA9B64DD85B03A56A9871FD6BB8F2CF1DBB\"\n" +
            "        }\n" +
            "\t]\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "If data is missing, fields should default to empty strings `\"\"`, and `indicators` should default to an empty array `[]`.\n" +
            "\n" +
            "### Example Output\n" +
            "**Output**\n" +
            "```json\n" +
            "{\n" +
            "\t\"name\" : \"Malware Activity Detected\",\n" +
            "\t\"description\" : \"Brief Description of the Report\",\n" +
            "\t\"created\": \"22-01-2025\",\n" +
            "\t\"indicators\": [\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"ipv4\",\n" +
            "\t\t\t\"value\": \"99.88.12.12\"\n" +
            "\t\t\t\"description\": \"small description of how this indicator was used.\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"domain\",\n" +
            "\t\t\t\"value\": \"www.abc.com\"\n" +
            "\t\t\t\"description\": \"small description of how this indicator was used.\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"url\",\n" +
            "\t\t\t\"value\": \"www.abc.com/file.hta\"\n" +
            "\t\t\t\"description\": \"small description of how this indicator was used.\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"email\",\n" +
            "\t\t\t\"value\": \"akamplan@protonmail.com\"\n" +
            "\t\t\t\"description\": \"small description of how this indicator was used.\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"type\": \"hash\",\n" +
            "            \"file\" : \"asd.bat\"\n" +
            "            \"md5\": \"438448FDC7521ED034F6DABDF814B6BA\"\n" +
            "            \"sha1\": \"F08E7343A94897ADEAE78138CC3F9142ED160A03\"\n" +
            "            \"sha256\": \"1E2E25A996F72089F12755F931E7FCA9B64DD85B03A56A9871FD6BB8F2CF1DBB\"\n" +
            "\t\t\t\"description\": \"small description of how this indicator was used.\"\n" +
            "\t\t}\n" +
            "\t],\n" +
            "}\n" +
            "```\n" +
            "\n" +
            "# Notes\n" +
            "\n" +
            "- Ensure proper validation for indicator types and strict JSON formatting.\n" +
            "- Any text or fields outside the specified JSON structure must not be included in the response.\n";
            
    private static final String EXTRACT_TECHNIQUES_PROMPT = "You are an AI assistant specializing in cybersecurity threat intelligence analysis. " +
            "Your task is to extract MITRE ATT&CK techniques from the provided document." +
            "\\n\\n## Extraction Requirements:\\n- Extract **distinct** MITRE ATT&CK techniques from both **text** and **tables**." +
            "\\n- Ensure each technique includes:\\n  - `\\\"technique_id\\\"`: The unique MITRE ATT&CK technique identifier (e.g., T1190)." +
            "\\n  - `\\\"technique_name\\\"`: The official name of the MITRE ATT&CK technique." +
            "\\n  - `\\\"technique_usage\\\"`: The description of how the technique was used in the given context." +
            "\\n\\n## Handling Data from Different Sources:\\n1. **Table Data Handling**:" +
            "\\n   - If the technique is found in a table, extract the exact " +
            "`\\\"technique_id\\\"`, `\\\"technique_name\\\"`, and `\\\"technique_usage\\\"` from the table." +
            "\\n   - Ensure data extracted from tables maintains the correct mapping.\\n\\n2. **Text Data Handling**:" +
            "\\n   - If a MITRE ATT&CK technique appears in text, extract its ID, name, and a **summarized usage description**." +
            "\\n   - If the same technique appears in both a table and text, include **both sources** in `\\\"technique_usage\\\"`, " +
            "clearly distinguishing `\\\"from_table\\\"` and `\\\"from_text\\\"`.\\n\\n## Expected Output Format:" +
            "\\nReturn a JSON object with the extracted techniques inside a `\\\"techniques\\\"` " +
            "field:\\n```json\\n{\\n  \\\"techniques\\\": [\\n    {\\n      \\\"technique_id\\\": \\\"T1190\\\"," +
            "\\n      \\\"technique_name\\\": \\\"Exploit Public-Facing Application\\\",\\n      " +
            "\\\"technique_usage\\\": {\\n        \\\"from_table\\\": \\\"Daixin actors exploited an unpatched " +
            "vulnerability in a VPN server to gain initial access.\\\",\\n        " +
            "\\\"from_text\\\": \\\"Daixin actors compromised a VPN server by exploiting an unpatched vulnerability " +
            "to gain network entry.\\\"\\n      }\\n    },\\n    ...\\n  ]\\n}\n";

    private static final String EXTRACT_DFIR_TECHNIQUES_PROMPT = "You are an expert cybersecurity analyst specializing in the MITRE ATT&CK framework. Your task is to analyze text content from security reports and extract potential MITRE ATT&CK techniques and procedures that are implied in the text, even when not explicitly mentioned with technique IDs. For each identified technique:\n\n" +
        "1. Predict the most likely MITRE ATT&CK technique ID (e.g., T1015).\n" +
        "2. Provide the official MITRE technique name.\n" +
        "3. Extract the specific sentence or phrase from the text that led to your prediction.\n" +
        "4. Only include techniques where you have high confidence based on the context.\n\n" +
        "Return the results in JSON format with this structure:\n" +
        "[\n" +
        "    {\n" +
        "        \"technique_id\": \"TXXXX\",\n" +
        "        \"technique_name\": \"Official MITRE Technique Name\",\n" +
        "        \"technique_procedure\": \"The specific sentence or phrase from the text\"\n" +
        "    }\n" +
        "]\n" +
        "If no techniques can be confidently identified, return an empty array [].\n";

    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getPreprocessedContent(String url) {
        log.info("Calling Python API to preprocess content from: {}", url);
        
        WebClient webClient = webClientBuilder
                .baseUrl(pythonApiBaseUrl)
                .build();
        
        Map<String, Object> requestBody = Map.of("pdf_path", url);
        
        return webClient.post()
                .uri("/pdf-parser/preprocess")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (Map<String, Object>) response)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(response -> log.info("Successfully preprocessed content from: {}", url))
                .doOnError(error -> log.error("Error during preprocessing call for {}: {}", url, error.getMessage()));
    }
    
    /**
     * Calls the Python API to extract intelligence from already processed content.
     * rawContent and filteredContent are passed as Objects, expecting them to be
     * List<Map<String, Object>> for structured data, which will be serialized to JSON arrays.
     */
    public Mono<Map<String, Object>> extractIntel(String url, List<Map<String, Object>> filteredContent,
                                                  List<Map<String, Object>> rawContent, List<?> dfirData, Map<String, Object> metadata,
                                                  String provider) {
        log.info("Attempting to extract intelligence internally for URL: {} using AzureOpenAIService", url);

        // Prepare content for different LLM calls
        String indicatorsContent = prepareContentForLlm(rawContent);
        String techniquesContent = prepareContentForLlm(filteredContent);

        if (indicatorsContent.isEmpty() && techniquesContent.isEmpty() && (dfirData == null || dfirData.isEmpty())) {
            log.warn("No textual content to process for URL: {}", url);
            return Mono.just(convertDtoToMap(null, url));
        }

        // --- Direct LLM calls using AzureOpenAIService (Reactive) ---
        Mono<String> indicatorsResponseMono = !indicatorsContent.isEmpty()
                ? azureOpenAIService.getChatCompletionAsync("azure-1", EXTRACT_INDICATORS_PROMPT, indicatorsContent)
                        .doOnError(e -> log.error("Error getting indicators response for {}", url, e))
                        .defaultIfEmpty("{}")
                : Mono.just("{}");

        Mono<String> techniquesResponseMono = !techniquesContent.isEmpty()
                ? azureOpenAIService.getChatCompletionAsync("azure-1", EXTRACT_TECHNIQUES_PROMPT, techniquesContent)
                        .doOnError(e -> log.error("Error getting techniques response for {}", url, e))
                        .defaultIfEmpty("{}")
                : Mono.just("{}");
        
        Mono<List<Map<String, Object>>> dfirTechniquesListMono;
        if (dfirData != null && !dfirData.isEmpty()) {
            dfirTechniquesListMono = Flux.fromIterable(dfirData)
                .flatMap(chunkAsObject -> { // It's a raw object from a heterogenous list, expecting List<Map>
                    if (!(chunkAsObject instanceof List)) {
                        log.warn("Skipping DFIR chunk because it is not a List: {}", chunkAsObject);
                        return Mono.empty();
                    }
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> chunkPages = (List<Map<String, Object>>) chunkAsObject;
                    
                    String content = chunkPages.stream()
                        .map(page -> page.getOrDefault("content", "").toString())
                        .filter(s -> !s.trim().isEmpty())
                        .collect(Collectors.joining("\n\n---\n\n"));

                    if (content.isEmpty()) {
                        return Mono.empty();
                    }
                    
                    return azureOpenAIService.getChatCompletionAsync("azure-2", EXTRACT_DFIR_TECHNIQUES_PROMPT, content)
                            .doOnError(e -> log.error("Error getting DFIR techniques for a chunk for {}", url, e))
                            .onErrorReturn("[]"); // Return empty list string on error for one chunk
                }) // No global limit needed - per-client limits handle this
                .flatMap(jsonResponse -> {
                    try {
                        List<Map<String, Object>> techniques = objectMapper.readValue(jsonResponse, new TypeReference<List<Map<String, Object>>>() {});
                        return Flux.fromIterable(techniques);
                    } catch (Exception e) {
                        log.error("Error parsing DFIR techniques chunk for {}: {}", url, jsonResponse, e);
                        return Flux.empty();
                    }
                })
                .collectList()
                .defaultIfEmpty(Collections.emptyList());
        } else {
            dfirTechniquesListMono = Mono.just(Collections.emptyList());
        }

        // Process in parallel with per-client concurrency control
        return Mono.zip(
                indicatorsResponseMono.flatMap(this::safelyParseIndicatorsResponse),
                techniquesResponseMono.flatMap(this::safelyParseTechniquesResponse),
                dfirTechniquesListMono)
            .flatMap(tuple -> {
                Map<String, Object> indicatorsMap = tuple.getT1();
                Map<String, Object> techniquesMap = tuple.getT2();
                List<Map<String, Object>> dfirTechniquesList = tuple.getT3();

                log.info("LLM Indicators Response: {}", indicatorsMap);
                log.info("LLM Techniques Response: {}", techniquesMap);
                log.info("LLM DFIR Techniques (aggregated from {} chunks): {}", dfirData != null ? dfirData.size() : 0, dfirTechniquesList);

                try {
                    // 1. Parse LLM responses into our new, local DTO
                    IntelligenceReportDTO report = new IntelligenceReportDTO();
                    
                    report.setName((String) indicatorsMap.getOrDefault("name", url));
                    report.setDescription((String) indicatorsMap.get("description"));

                    // 2. Map Indicators
                    if (indicatorsMap.containsKey("indicators")) {
                        report.setIndicators(objectMapper.convertValue(indicatorsMap.get("indicators"), new TypeReference<List<IntelligenceReportDTO.Indicator>>() {}));
                    }

                    // 3. Merge and Map Techniques
                    Map<String, IntelligenceReportDTO.Technique> finalTechniques = new LinkedHashMap<>();

                    // Process primary techniques
                    if (techniquesMap.containsKey("techniques")) {
                        List<IntelligenceReportDTO.Technique> primaryTechniques = objectMapper.convertValue(techniquesMap.get("techniques"), new TypeReference<List<IntelligenceReportDTO.Technique>>() {});
                        for (IntelligenceReportDTO.Technique tech : primaryTechniques) {
                            finalTechniques.put(tech.getTechnique_id(), tech);
                        }
                    }

                    // Process and merge DFIR techniques
                    if (dfirTechniquesList != null && !dfirTechniquesList.isEmpty()) {
                        for (Map<String, Object> extract : dfirTechniquesList) {
                            String techId = (String) extract.get("technique_id");
                            if (techId == null) continue;

                            String procedure = (String) extract.get("technique_procedure");
                            
                            if (finalTechniques.containsKey(techId)) {
                                // Technique exists, append usage
                                IntelligenceReportDTO.Technique existingTech = finalTechniques.get(techId);
                                Map<String, String> usage = existingTech.getTechnique_usage();
                                if (usage == null) {
                                    usage = new HashMap<>();
                                }
                                String existingProcedure = usage.getOrDefault("from_dfir", "");
                                if (procedure != null && !procedure.isEmpty()) {
                                    usage.put("from_dfir", (existingProcedure.isEmpty() ? "" : existingProcedure + "\n") + "* " + procedure);
                                }
                                existingTech.setTechnique_usage(usage);

                            } else {
                                // New technique from DFIR extract
                                IntelligenceReportDTO.Technique newTech = new IntelligenceReportDTO.Technique();
                                newTech.setTechnique_id(techId);
                                newTech.setTechnique_name((String) extract.get("technique_name"));
                                Map<String, String> usage = new HashMap<>();
                                if (procedure != null && !procedure.isEmpty()) {
                                    usage.put("from_dfir", "* " + procedure);
                                }
                                newTech.setTechnique_usage(usage);
                                finalTechniques.put(techId, newTech);
                            }
                        }
                    }

                    report.setTechniques(new ArrayList<>(finalTechniques.values()));
                    
                    // 4. Transform the DTO into the desired final map structure, matching the user's example
                    Map<String, Object> finalReport = new LinkedHashMap<>();
                    finalReport.put("id", "malware--" + UUID.randomUUID().toString());
                    finalReport.put("name", report.getName());
                    finalReport.put("type", "malware");
                    
                    // Date handling
                    String createdDateStr = (String) indicatorsMap.get("created");
                    String isoDate = Instant.now().toString(); // Default to now
                    if (createdDateStr != null && !createdDateStr.isEmpty()) {
                        try {
                             // Assuming format like DD-MM-YYYY, needs to be parsed and formatted
                            isoDate = java.time.format.DateTimeFormatter.ISO_INSTANT.format(
                                java.time.LocalDate.parse(createdDateStr, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
                        } catch (java.time.format.DateTimeParseException e) {
                            log.warn("Could not parse date '{}', defaulting to now.", createdDateStr);
                        }
                    }
                    finalReport.put("created", isoDate);

                    // Reports array
                    Map<String, String> reportInfo = new LinkedHashMap<>();
                    reportInfo.put("title", report.getName());
                    reportInfo.put("report_url", url);
                    reportInfo.put("report_type", "External Reference");
                    finalReport.put("reports", Collections.singletonList(reportInfo));

                    finalReport.put("indicators", report.getIndicators() != null ? report.getIndicators() : Collections.emptyList());

                    List<Map<String, Object>> finalTechniquesList = new ArrayList<>();
                    if (report.getTechniques() != null) {
                        for (IntelligenceReportDTO.Technique tech : report.getTechniques()) {
                            Map<String, Object> finalTech = new LinkedHashMap<>();
                            finalTech.put("id", tech.getTechnique_id());
                            finalTech.put("name", tech.getTechnique_name());

                            // Process and merge usage details
                            List<String> finalUsageLines = new ArrayList<>();
                            Set<String> seenLines = new HashSet<>();

                            if (tech.getTechnique_usage() != null) {
                                // Process usage from tables first, without a prefix
                                String fromTable = tech.getTechnique_usage().get("from_table");
                                if (fromTable != null && !fromTable.trim().isEmpty()) {
                                     String cleaned = fromTable.trim().replaceAll("[\"“”]", "");
                                     if (seenLines.add(cleaned)) {
                                         finalUsageLines.add(cleaned);
                                     }
                                }

                                // Process usage from DFIR next, with a '*' prefix
                                String fromDfir = tech.getTechnique_usage().get("from_dfir");
                                if (fromDfir != null && !fromDfir.trim().isEmpty()) {
                                    String[] dfirLines = fromDfir.split("\\n");
                                    for (String line : dfirLines) {
                                        String cleanedLine = line.trim().replaceAll("^\\*\\s*", "").replaceAll("[\"“”]", "");
                                        if (!cleanedLine.isEmpty() && seenLines.add(cleanedLine)) {
                                            finalUsageLines.add("* " + cleanedLine);
                                        }
                                    }
                                }
                            }
                            
                            String combinedUsage = String.join("\n", finalUsageLines);
                            
                            if(combinedUsage.isEmpty()){
                                combinedUsage = "No detailed information available";
                            }

                            Map<String, Object> usageDetail = new LinkedHashMap<>();
                            usageDetail.put("name", "AI Report");
                            usageDetail.put("usage", combinedUsage);
                            
                            finalTech.put("usage", Collections.singletonList(usageDetail));
                            finalTech.put("sub_techniques", Collections.emptyList());
                            finalTech.put("entities", Collections.emptyList());

                            finalTechniquesList.add(finalTech);
                        }
                    }
                    finalReport.put("techniques", finalTechniquesList);
                    finalReport.put("description", report.getDescription());
                    finalReport.put("last_modified", isoDate);


                    return Mono.just(finalReport);

                } catch (Exception e) {
                    log.error("Error processing LLM responses for {}: {}", url, e.getMessage(), e);
                    return Mono.error(e);
                }
            });
    }
    
    private Mono<Map<String, Object>> safelyParseIndicatorsResponse(String jsonString) {
        try {
            // The response might be an empty string, which is not valid JSON. Default to an empty map.
            if (jsonString == null || jsonString.trim().isEmpty() || jsonString.trim().equals("{}")) {
                return Mono.just(Collections.emptyMap());
            }
            return Mono.just(objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {}));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse INDICATORS JSON response due to malformed or incomplete stream for content. Content starts with: '{}...'", getSnippet(jsonString), e);
            return Mono.error(e); // Propagate the error to be caught by the main pipeline
        }
    }

    private Mono<Map<String, Object>> safelyParseTechniquesResponse(String jsonString) {
        try {
            if (jsonString == null || jsonString.trim().isEmpty() || jsonString.trim().equals("{}")) {
                return Mono.just(Collections.emptyMap());
            }
            return Mono.just(objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {}));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse TECHNIQUES JSON response due to malformed or incomplete stream for content. Content starts with: '{}...'", getSnippet(jsonString), e);
            return Mono.error(e);
        }
    }
    
    private Flux<Map<String, Object>> safelyParseDfirResponse(String jsonString) {
        try {
            if (jsonString == null || jsonString.trim().isEmpty() || jsonString.trim().equals("[]")) {
                return Flux.empty();
            }
            List<Map<String, Object>> techniques = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {});
            return Flux.fromIterable(techniques);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Error parsing DFIR techniques chunk due to malformed or incomplete stream. Content starts with: '{}...'", getSnippet(jsonString), e);
            return Flux.error(e);
        }
    }
    
    private String getSnippet(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, Math.min(text.length(), 150));
    }
    
    private String prepareContentForLlm(List<Map<String, Object>> contentToProcess) {
        StringBuilder textContentBuilder = new StringBuilder();

        if (contentToProcess != null) {
            for (Map<String, Object> pageBlock : contentToProcess) { // Each item is a page
                if (pageBlock == null) continue;

                Object elementsObject = pageBlock.get("elements");
                if (elementsObject instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> elements = (List<Map<String, Object>>) elementsObject;

                    for (Map<String, Object> elementBlock : elements) { // Each item is a text or table block
                        if (elementBlock == null) continue;

                        Object textData = elementBlock.get("content");

                        if (textData instanceof String) {
                            textContentBuilder.append((String) textData).append("\n\n");
                        } else if (textData != null) {
                            try {
                                // Handle cases where content might be structured (e.g., table data)
                                textContentBuilder.append(objectMapper.writeValueAsString(textData)).append("\n\n");
                            } catch (Exception e) {
                                log.warn("Could not serialize content block to string.", e);
                            }
                        }
                    }
                }
            }
        }
        return textContentBuilder.toString();
    }

    private Map<String, Object> convertDtoToMap(GenericEntityDTO dto, String reportUrlFromInput) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (dto == null) {
            map.put("id", "report--" + UUID.randomUUID().toString());
            map.put("type", "report");
            map.put("name", "Unknown Report");
            map.put("description", "No data processed or error during processing.");
            map.put("reports", Collections.emptyList());
            map.put("created", Instant.now().toString());
            map.put("last_modified", Instant.now().toString());
            map.put("techniques", Collections.emptyList());
            map.put("indicators", Collections.emptyList());
            return map;
        }

        map.put("id", dto.getId());
        map.put("type", dto.getType() != null ? dto.getType() : "report");
        map.put("name", dto.getName());
        map.put("description", dto.getDescription());

        List<Map<String, Object>> reportList = new ArrayList<>();
        if (dto.getReports() != null && !dto.getReports().isEmpty()) {
            for (ReportSummary summary : dto.getReports()) {
                Map<String, Object> reportEntry = new LinkedHashMap<>();
                reportEntry.put("title", summary.getTitle());
                reportEntry.put("report_url", summary.getReportUrl());
                reportEntry.put("report_type", summary.getReportType());
                reportList.add(reportEntry);
            }
        } else if (reportUrlFromInput != null && !reportUrlFromInput.isEmpty()) {
            Map<String, Object> reportEntry = new LinkedHashMap<>();
            reportEntry.put("title", dto.getName() != null ? dto.getName() : "Source Report");
            reportEntry.put("report_url", reportUrlFromInput);
            reportEntry.put("report_type", "External Reference");
            reportList.add(reportEntry);
        }
        map.put("reports", reportList);

        map.put("created", dto.getCreated() != null ? dto.getCreated().toString() : Instant.now().toString());
        map.put("last_modified", dto.getLastModified() != null ? dto.getLastModified().toString() : Instant.now().toString());

        if (dto.getTechniques() != null) {
            map.put("techniques", dto.getTechniques().stream().map(this::convertTechniqueDtoToMap).collect(Collectors.toList()));
        } else {
            map.put("techniques", Collections.emptyList());
        }

        map.put("indicators", Collections.emptyList());
        return map;
    }

    private Map<String, Object> convertTechniqueDtoToMap(TechniqueDTO techDto) {
        Map<String, Object> techMap = new LinkedHashMap<>();
        techMap.put("id", techDto.getTechniqueId());
        techMap.put("name", techDto.getName());
        
        Map<String, Object> usageDetail = new LinkedHashMap<>();
        usageDetail.put("name", "AI Report"); 

        String combinedUsage = "No detailed information available";
        if (techDto.getUsage() != null && !techDto.getUsage().isEmpty()) {
            combinedUsage = techDto.getUsage().stream()
                                .map(EntityUsage::getUsage)
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining("\\n* ", "* ", ""));
            if (combinedUsage.trim().equals("*") || combinedUsage.trim().isEmpty()) {
                 combinedUsage = "No detailed information available";
            }
        } else if (techDto.getDescription() != null && !techDto.getDescription().isEmpty()) {
            combinedUsage = techDto.getDescription();
        }
        usageDetail.put("usage", combinedUsage);
        techMap.put("usage", Collections.singletonList(usageDetail));
        
        if (techDto.getSubTechniques() != null && !techDto.getSubTechniques().isEmpty()) {
            techMap.put("sub_techniques", techDto.getSubTechniques().stream()
                .map(subTech -> {
                    Map<String, Object> subTechMap = new LinkedHashMap<>();
                    subTechMap.put("id", subTech.getId());
                    subTechMap.put("name", subTech.getName());
                    return subTechMap;
                }).collect(Collectors.toList()));
        } else {
            techMap.put("sub_techniques", Collections.emptyList());
        }

        if (techDto.getEntities() != null && !techDto.getEntities().isEmpty()) {
            techMap.put("entities", techDto.getEntities().stream()
                .map(entityView -> {
                    Map<String, Object> entityMap = new LinkedHashMap<>();
                    entityMap.put("id", entityView.getId());
                    entityMap.put("name", entityView.getName());
                    entityMap.put("type", entityView.getType());
                    return entityMap;
                }).collect(Collectors.toList()));
        } else {
            techMap.put("entities", Collections.emptyList());
        }
        return techMap;
    }
    
    /**
     * Calls the database API to store the processed intelligence
     * This corresponds to the parse_data_to_db function in the Python code
     */
    public Mono<Void> storeIntelligenceData(Map<String, Object> responseData) {
        return Mono.fromRunnable(() -> {
            try {
                ThreatIntelDoc doc = new ThreatIntelDoc();
                doc.setId(UUID.randomUUID().toString());
                doc.setTimestamp(Instant.now());
                doc.setIntel(responseData);

                if (responseData.containsKey("report_url")) {
                    doc.setReportUrl(responseData.get("report_url").toString());
                }

                threatIntelRepository.save(doc);
                log.info("Successfully stored threat intelligence for report: {}", doc.getReportUrl());

            } catch (Exception e) {
                log.error("Error storing intelligence data to Elasticsearch", e);
                // We can choose to re-throw or handle it. For now, just logging.
            }
        });
    }
    
    /**
     * Health check for the Python API
     */
    public Mono<Boolean> isHealthy() {
        WebClient webClient = webClientBuilder
                .baseUrl(pythonApiBaseUrl)
                .build();
        
        return webClient.get()
                .uri("/")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> true)
                .onErrorReturn(false);
    }
} 
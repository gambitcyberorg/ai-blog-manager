package org.gc.aiagents.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gc.aiagents.domain.dto.BlogContentResponse;
import org.gc.aiagents.domain.dto.ParentBlogRequest;
import org.gc.aiagents.domain.dto.StatusResponse;
import org.gc.aiagents.service.BlogManagerService;
import org.gc.aiagents.service.PythonApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/blog-manager")
@RequiredArgsConstructor
public class BlogManagerController {
    
    private final BlogManagerService blogManagerService;
    private final PythonApiService pythonApiService;
    
    /**
     * Adds a new parent blog URL to the scanning schedule.
     * The first scan will be scheduled to run as soon as the scheduler picks it up.
     * 
     * Example:
     * POST /blog-manager/add-parent-blog
     * {
     *   "parentUrl": "https://thedfirreport.com/",
     *   "scanIntervalHours": 24
     * }
     */
    @PostMapping("/add-parent-blog")
    public Mono<ResponseEntity<StatusResponse>> addParentBlog(@Valid @RequestBody ParentBlogRequest request) {
        log.info("Received request to add parent blog: {}", request.getParentUrl());
        
        return blogManagerService.addParentBlogForScanning(request.getParentUrl(), request.getScanIntervalHours())
                .map(schedule -> {
                    Map<String, Object> details = Map.of(
                            "parent_url", schedule.getParentUrl(),
                            "parent_uid", schedule.getParentUid(),
                            "scan_interval_hours", schedule.getScanIntervalHours(),
                            "next_scan", schedule.getNextScanDatetimeUtc().toString()
                    );
                    return ResponseEntity.ok(StatusResponse.success(
                            "Parent blog URL submitted for scanning.", details));
                })
                .onErrorResume(error -> {
                    log.error("Error adding parent blog {}: {}", request.getParentUrl(), error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(StatusResponse.builder()
                                    .message("Failed to add parent blog: " + error.getMessage())
                                    .build()));
                });
    }
    
    /**
     * Extracts content from a given blog URL and returns it directly to the user.
     * This calls the Python API for processing.
     * 
     * Example:
     * GET /blog-manager/extract-blog-content?url=https://thedfirreport.com/2024/03/04/some-article
     */
    @GetMapping("/extract-blog-content")
    public Mono<ResponseEntity<Map<String, Object>>> extractBlogContent(
            @RequestParam String url,
            @RequestParam(defaultValue = "azure") String provider) {
        
        log.info("Received request to extract content from: {} using provider: {}", url, provider);
        
        return pythonApiService.getPreprocessedContent(url)
            .flatMap(preprocessedDataMap -> {
                Object rawDataObj = preprocessedDataMap.get("raw_data");
                Object filteredDataObj = preprocessedDataMap.get("filtered_data");
                Object dfirDataObj = preprocessedDataMap.get("dfir_data");

                // Ensure they are lists, default to empty lists if null
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawData = (rawDataObj instanceof List) ? (List<Map<String, Object>>) rawDataObj : new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> filteredData = (filteredDataObj instanceof List) ? (List<Map<String, Object>>) filteredDataObj : new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<?> dfirData = (dfirDataObj instanceof List) ? (List<?>) dfirDataObj : new ArrayList<>();
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) preprocessedDataMap.getOrDefault("metadata", Map.of());
                
                log.info("Preprocessed content obtained for {}, proceeding to internal intelligence extraction.", url);
                // Call the new internal Java LLM method
                return pythonApiService.extractIntel(url, filteredData, rawData, dfirData, metadata, provider);
            })
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("Error extracting content from {}: {}", url, error.getMessage());
                // Create a simple error response map
                Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "articleUrl", url,
                    "errorMessage", error.getMessage()
                );
                return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
            });
    }
    
    /**
     * Returns a list of UIDs for all parent blogs.
     * 
     * Example:
     * GET /blog-manager/parent-blogs-uids
     */
    @GetMapping("/parent-blogs-uids")
    public Mono<ResponseEntity<List<String>>> getParentBlogsUids() {
        log.info("Received request to get parent blog UIDs");
        
        return blogManagerService.getParentBlogUids()
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error fetching parent blog UIDs: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(List.of()));
                });
    }
    
    /**
     * Returns all child blogs associated with a given parent blog UID.
     * 
     * Example:
     * GET /blog-manager/child-blogs/236b3a62-5e81-45a9-bd01-58b2ecff2882
     */
    @GetMapping("/child-blogs/{parentUid}")
    public Mono<ResponseEntity<List<Map<String, String>>>> getChildBlogs(@PathVariable String parentUid) {
        log.info("Received request to get child blogs for parent UID: {}", parentUid);
        
        return blogManagerService.getChildBlogs(parentUid)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error fetching child blogs for parent UID {}: {}", parentUid, error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(List.of()));
                });
    }
    
    /**
     * Manually triggers a scan for a specific parent blog URL.
     * This is useful for testing or immediate scanning.
     * 
     * Example:
     * POST /blog-manager/trigger-scan
     * {
     *   "parentUrl": "https://thedfirreport.com/"
     * }
     */
    @PostMapping("/trigger-scan")
    public ResponseEntity<StatusResponse> triggerScan(@RequestBody Map<String, String> request) {
        String parentUrl = request.get("parentUrl");
        String parentUid = request.get("parentUid");
        
        if (parentUrl == null || parentUid == null) {
            return ResponseEntity.badRequest()
                    .body(StatusResponse.builder()
                            .message("Both parentUrl and parentUid are required")
                            .build());
        }
        
        log.info("Received request to trigger scan for: {}", parentUrl);
        
        // Start the scan asynchronously
        blogManagerService.startScanForParentBlog(parentUid, parentUrl);
        
        // Return immediately with 202 Accepted
        return ResponseEntity.accepted()
                .body(StatusResponse.success("Scan accepted for processing for " + parentUrl));
    }
    
    /**
     * Manually triggers scheduled scans for all due parent blogs.
     * This is useful for testing the scheduler functionality.
     * 
     * Example:
     * POST /blog-manager/run-scheduled-scans
     */
    @PostMapping("/run-scheduled-scans")
    public Mono<ResponseEntity<StatusResponse>> runScheduledScans() {
        log.info("Received request to run scheduled scans");
        
        return blogManagerService.runScheduledParentScans()
                .then(Mono.just(ResponseEntity.ok(StatusResponse.success("Scheduled scans completed"))))
                .onErrorResume(error -> {
                    log.error("Error running scheduled scans: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(StatusResponse.builder()
                                    .message("Failed to run scheduled scans: " + error.getMessage())
                                    .build()));
                });
    }
    
    /**
     * Manually triggers retry of failed articles.
     * This is useful for testing the retry functionality.
     * 
     * Example:
     * POST /blog-manager/retry-failed-articles
     */
    @PostMapping("/retry-failed-articles")
    public Mono<ResponseEntity<StatusResponse>> retryFailedArticles() {
        log.info("Received request to retry failed articles");
        
        return blogManagerService.retryFailedArticles()
                .then(Mono.just(ResponseEntity.ok(StatusResponse.success("Failed articles retry completed"))))
                .onErrorResume(error -> {
                    log.error("Error retrying failed articles: {}", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(StatusResponse.builder()
                                    .message("Failed to retry articles: " + error.getMessage())
                                    .build()));
                });
    }
    
    /**
     * Health check endpoint for the Python API.
     * 
     * Example:
     * GET /blog-manager/python-api-health
     */
    @GetMapping("/python-api-health")
    public Mono<ResponseEntity<StatusResponse>> checkPythonApiHealth() {
        log.info("Checking Python API health");
        
        return pythonApiService.isHealthy()
                .map(healthy -> {
                    if (healthy) {
                        return ResponseEntity.ok(StatusResponse.success("Python API is healthy"));
                    } else {
                        return ResponseEntity.status(503)
                                .body(StatusResponse.builder()
                                        .message("Python API is not responding")
                                        .build());
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error checking Python API health: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(503)
                            .body(StatusResponse.builder()
                                    .message("Failed to check Python API health: " + error.getMessage())
                                    .build()));
                });
    }
} 
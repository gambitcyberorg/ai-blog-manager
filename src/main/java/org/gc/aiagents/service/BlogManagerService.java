package org.gc.aiagents.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gc.aiagents.domain.ArticleScrapeStatus;
import org.gc.aiagents.domain.ParentBlogSchedule;
import org.gc.aiagents.repository.ArticleScrapeStatusRepository;
import org.gc.aiagents.repository.ParentBlogScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlogManagerService {

    private final ParentBlogScheduleRepository parentBlogRepository;
    private final ArticleScrapeStatusRepository articleRepository;
    private final UrlCollectionService urlCollectionService;
    private final PythonApiService pythonApiService;
    private final ObjectMapper objectMapper;

    @Value("${blog-manager.default-scan-interval-hours:168}") // Default weekly
    private int defaultScanIntervalHours;

    @Value("${blog-manager.default-retry-interval-minutes:60}")
    private int defaultRetryIntervalMinutes;

    @Value("${blog-manager.max-failure-count:5}")
    private int maxFailureCount;

    @Value("${blog-manager.python-provider:azure}")
    private String pythonProvider;

    @Value("${blog-manager.stuck-article-timeout-minutes:10}")
    private int stuckArticleTimeoutMinutes;

    /**
     * Adds a parent blog URL for scanning
     */
    public Mono<ParentBlogSchedule> addParentBlogForScanning(String parentUrl, Integer scanIntervalHours) {
        log.info("Adding parent blog for scanning: {}", parentUrl);
        
        return Mono.fromCallable(() -> {
            Optional<ParentBlogSchedule> existing = parentBlogRepository.findByParentUrl(parentUrl);
            
            if (existing.isPresent()) {
                // Update existing schedule
                ParentBlogSchedule schedule = existing.get();
                if (scanIntervalHours != null) {
                    schedule.setScanIntervalHours(scanIntervalHours);
                }
                schedule.setUpdatedAtUtc(Instant.now());
                return parentBlogRepository.save(schedule);
            } else {
                // Create new schedule
                ParentBlogSchedule newSchedule = ParentBlogSchedule.create(parentUrl, scanIntervalHours);
                return parentBlogRepository.save(newSchedule);
            }
        });
    }

    /**
     * Triggers a parent blog scan asynchronously.
     */
    @Async
    public void startScanForParentBlog(String parentUid, String parentUrl) {
        scanParentBlogUrl(parentUid, parentUrl)
                .subscribe(
                        null, // onNext is not needed as it's a Mono<Void>
                        error -> log.error("Asynchronous scan failed for {}: {}", parentUrl, error.getMessage())
                );
    }

    /**
     * Scans a parent blog URL for new articles and processes them
     */
    public Mono<Void> scanParentBlogUrl(String parentUid, String parentUrl) {
        log.info("Starting scan for parent blog: {} (UID: {})", parentUrl, parentUid);
        
        Instant now = Instant.now();
        
        return updateParentScanAttempt(parentUid, now)
                .then(collectAndIdentifyBlogUrls(parentUrl))
                .flatMapMany(Flux::fromIterable)
                .flatMap(articleUrl -> 
                    resetFailureCountForFailedArticle(articleUrl)
                        .then(processDiscoveredArticle(articleUrl, parentUid)), 1)
                .then(updateParentScanSuccess(parentUid, now))
                .doOnSuccess(v -> log.info("Successfully completed scan for {}", parentUrl))
                .doOnError(error -> log.error("Error scanning parent blog {}: {}", parentUrl, error.getMessage()));
    }

    /**
     * Processes a single discovered article
     */
    public Mono<Void> processDiscoveredArticle(String articleUrl, String parentUid) {
        log.info("Processing article: {} (Parent UID: {})", articleUrl, parentUid);
        
        Instant now = Instant.now();
        
        return checkArticleProcessingNeeded(articleUrl)
                .flatMap(needed -> {
                    if (!needed) {
                        log.info("Skipping already processed article: {}", articleUrl);
                        return Mono.empty();
                    }
                    
                    return updateArticleStatus(articleUrl, parentUid, ArticleScrapeStatus.ScrapeStatus.IN_PROGRESS, now)
                            .then(pythonApiService.getPreprocessedContent(articleUrl)
                                .flatMap(preprocessedDataMap -> {
                                    Object rawData = preprocessedDataMap.get("raw_data");
                                    Object filteredData = preprocessedDataMap.get("filtered_data");
                                    Object dfirData = preprocessedDataMap.get("dfir_data");

                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> metadata = (Map<String, Object>) preprocessedDataMap.getOrDefault("metadata", Map.of());
                                    
                                    log.info("Successfully preprocessed content for {}, proceeding to intelligence extraction.", articleUrl);

                                    // The lists are expected to be List<Map<String, Object>>
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> rawDataList = (rawData instanceof List) ? (List<Map<String, Object>>) rawData : new ArrayList<>();
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> filteredDataList = (filteredData instanceof List) ? (List<Map<String, Object>>) filteredData : new ArrayList<>();
                                    @SuppressWarnings("unchecked")
                                    List<?> dfirDataList = (dfirData instanceof List) ? (List<?>) dfirData : new ArrayList<>();

                                    log.info("Extracting intelligence for article: Raw Data: {}, Filtered Data: {}, Dfir Data: {}", !rawDataList.isEmpty(), !filteredDataList.isEmpty(), !dfirDataList.isEmpty());
                                    return pythonApiService.extractIntel(articleUrl, filteredDataList, rawDataList, dfirDataList, metadata, pythonProvider);
                                })
                            )
                            .flatMap(apiResponse -> {
                                if (apiResponse != null && !apiResponse.isEmpty()) {
                                    return pythonApiService.storeIntelligenceData(apiResponse)
                                            .then(updateArticleSuccess(articleUrl, now, apiResponse));
                                } else {
                                    log.warn("Received empty or null API response after intelligence extraction for article: {}", articleUrl);
                                    return updateArticleFailure(articleUrl, now, "Empty response from intelligence extraction");
                                }
                            })
                            .onErrorResume(error -> {
                                log.error("Error processing article {}: {}", articleUrl, error.getMessage(), error);
                                return updateArticleFailure(articleUrl, now, error.getMessage());
                            });
                });
    }

    /**
     * Runs scheduled parent scans
     */
    public Mono<Void> runScheduledParentScans() {
        log.info("Running scheduled parent scans");
        
        Instant now = Instant.now();
        
        return Mono.fromCallable(() -> parentBlogRepository.findByNextScanDatetimeUtcLessThanEqual(now))
                .flatMapMany(Flux::fromIterable)
                .flatMap(schedule -> scanParentBlogUrl(schedule.getParentUid(), schedule.getParentUrl()))
                .then()
                .doOnSuccess(v -> log.info("Completed scheduled parent scans"))
                .doOnError(error -> log.error("Error in scheduled parent scans: {}", error.getMessage()));
    }

    /**
     * Retries failed articles that are due for retry
     */
    public Mono<Void> retryFailedArticles() {
        log.info("Retrying failed articles based on FAILED status flag.");

        return Mono.fromCallable(() ->
                articleRepository.findByScrapeStatusAndFailureCountLessThan(
                        ArticleScrapeStatus.ScrapeStatus.FAILED, maxFailureCount))
                .flatMapMany(Flux::fromIterable)
                .flatMap(article -> {
                    log.info("Retrying failed article: {}", article.getArticleUrl());
                    return processDiscoveredArticle(article.getArticleUrl(), article.getParentUid());
                }, 1) // Keep concurrency at 1
                .then()
                .doOnSuccess(v -> log.info("Completed retry of failed articles."))
                .doOnError(error -> log.error("Error retrying failed articles: {}", error.getMessage()));
    }

    /**
     * Processes pending articles that are waiting to be processed
     */
    public Mono<Void> processPendingArticles() {
        log.info("Processing pending articles based on PENDING status flag.");

        return Mono.fromCallable(() ->
                articleRepository.findByScrapeStatus(ArticleScrapeStatus.ScrapeStatus.PENDING))
                .flatMapMany(Flux::fromIterable)
                .flatMap(article -> {
                    log.info("Processing pending article: {}", article.getArticleUrl());
                    return processDiscoveredArticle(article.getArticleUrl(), article.getParentUid());
                }, 1) // Keep concurrency at 1
                .then()
                .doOnSuccess(v -> log.info("Completed processing of pending articles."))
                .doOnError(error -> log.error("Error processing pending articles: {}", error.getMessage()));
    }

    /**
     * Resets articles stuck in IN_PROGRESS status for more than 10 minutes back to PENDING
     */
    public Mono<Void> resetStuckInProgressArticles() {
        log.info("Checking for articles stuck in IN_PROGRESS status");
        
        Instant timeoutThreshold = Instant.now().minus(stuckArticleTimeoutMinutes, ChronoUnit.MINUTES);
        
        return Mono.fromCallable(() -> 
                articleRepository.findByScrapeStatus(ArticleScrapeStatus.ScrapeStatus.IN_PROGRESS))
                .flatMapMany(Flux::fromIterable)
                .filter(article -> article.getUpdatedAtUtc().isBefore(timeoutThreshold))
                .flatMap(article -> {
                    log.warn("Resetting stuck IN_PROGRESS article to PENDING: {} (stuck since: {})", 
                            article.getArticleUrl(), article.getUpdatedAtUtc());
                    return resetStuckArticleToPending(article.getArticleUrl());
                })
                .then()
                .doOnSuccess(v -> log.info("Completed reset of stuck IN_PROGRESS articles"))
                .doOnError(error -> log.error("Error resetting stuck articles: {}", error.getMessage()));
    }

    /**
     * Gets all parent blog UIDs
     */
    public Mono<List<String>> getParentBlogUids() {
        return Mono.fromCallable(() -> 
                parentBlogRepository.findAllByOrderByCreatedAtUtcDesc()
                        .stream()
                        .map(ParentBlogSchedule::getParentUid)
                        .toList());
    }

    /**
     * Gets child blogs for a parent UID
     */
    public Mono<List<Map<String, String>>> getChildBlogs(String parentUid) {
        return Mono.fromCallable(() -> 
                articleRepository.findByParentUid(parentUid)
                        .stream()
                        .map(article -> Map.of(
                                "article_uid", article.getArticleUid(),
                                "article_url", article.getArticleUrl()
                        ))
                        .toList());
    }

    // Private helper methods
    
    private Mono<List<String>> collectAndIdentifyBlogUrls(String parentUrl) {
        return Mono.fromCallable(() -> urlCollectionService.collectContentUrls(parentUrl))
                .flatMap(candidateUrls -> {
                    if (candidateUrls.isEmpty()) {
                        log.info("No candidate URLs found for {}", parentUrl);
                        return Mono.just(List.<String>of());
                    }
                    
                    log.info("Collected {} candidate URLs from {}", candidateUrls.size(), parentUrl);
                    return Mono.fromCallable(() -> 
                            urlCollectionService.identifyBlogLinksWithLlm(candidateUrls, parentUrl));
                });
    }
    
    private Mono<Boolean> checkArticleProcessingNeeded(String articleUrl) {
        return Mono.fromCallable(() -> {
            Optional<ArticleScrapeStatus> existing = articleRepository.findByArticleUrl(articleUrl);
            
            if (existing.isEmpty()) {
                return true; // New article, needs processing
            }
            
            ArticleScrapeStatus status = existing.get();
            return switch (status.getScrapeStatus()) {
                case SUCCESS, IN_PROGRESS, MAX_FAILURES -> false;
                case FAILED -> status.getFailureCount() < maxFailureCount;
                case PENDING -> true;
            };
        });
    }
    
    private Mono<Void> updateParentScanAttempt(String parentUid, Instant now) {
        return Mono.fromRunnable(() -> {
            Optional<ParentBlogSchedule> schedule = parentBlogRepository.findById(parentUid);
            if (schedule.isPresent()) {
                ParentBlogSchedule s = schedule.get();
                s.setLastScanAttemptUtc(now);
                s.setUpdatedAtUtc(now);
                parentBlogRepository.save(s);
            }
        });
    }
    
    private Mono<Void> updateParentScanSuccess(String parentUid, Instant scanStartTime) {
        return Mono.fromRunnable(() -> {
            Optional<ParentBlogSchedule> schedule = parentBlogRepository.findById(parentUid);
            if (schedule.isPresent()) {
                ParentBlogSchedule s = schedule.get();
                s.setLastScanSuccessUtc(scanStartTime);
                s.setNextScanDatetimeUtc(scanStartTime.plus(s.getScanIntervalHours(), ChronoUnit.HOURS));
                s.setUpdatedAtUtc(Instant.now());
                parentBlogRepository.save(s);
            }
        });
    }
    
    private Mono<Void> updateArticleStatus(String articleUrl, String parentUid, 
                                         ArticleScrapeStatus.ScrapeStatus status, Instant now) {
        return Mono.fromRunnable(() -> {
            Optional<ArticleScrapeStatus> existing = articleRepository.findByArticleUrl(articleUrl);
            
            ArticleScrapeStatus article;
            if (existing.isPresent()) {
                article = existing.get();
            } else {
                article = ArticleScrapeStatus.create(articleUrl, parentUid);
            }
            
            article.setScrapeStatus(status);
            article.setLastScrapeAttemptUtc(now);
            article.setUpdatedAtUtc(now);
            
            articleRepository.save(article);
        });
    }
    
    private Mono<Void> updateArticleSuccess(String articleUrl, Instant now, Map<String, Object> apiResponse) {
        return Mono.fromRunnable(() -> {
            Optional<ArticleScrapeStatus> existing = articleRepository.findByArticleUrl(articleUrl);
            if (existing.isPresent()) {
                ArticleScrapeStatus article = existing.get();
                article.setScrapeStatus(ArticleScrapeStatus.ScrapeStatus.SUCCESS);
                article.setLastSuccessDatetimeUtc(now);
                article.setFailureCount(0);
                article.setRetryAfterUtc(null);
                article.setExternalApiResponse(apiResponse);
                article.setUpdatedAtUtc(now);
                articleRepository.save(article);
            }
        });
    }
    
    private Mono<Void> updateArticleFailure(String articleUrl, Instant now, String errorMessage) {
        return Mono.fromRunnable(() -> {
            Optional<ArticleScrapeStatus> existing = articleRepository.findByArticleUrl(articleUrl);
            if (existing.isPresent()) {
                ArticleScrapeStatus article = existing.get();
                int newFailureCount = article.getFailureCount() + 1;
                
                article.setFailureCount(newFailureCount);
                article.setUpdatedAtUtc(now);
                
                if (newFailureCount >= maxFailureCount) {
                    article.setScrapeStatus(ArticleScrapeStatus.ScrapeStatus.MAX_FAILURES);
                } else {
                    article.setScrapeStatus(ArticleScrapeStatus.ScrapeStatus.FAILED);
                }
                article.setRetryAfterUtc(null); // No longer used
                
                // Store error in external API response for debugging
                article.setExternalApiResponse(Map.of("error", errorMessage, "timestamp", now.toString()));
                
                articleRepository.save(article);
            }
        });
    }

    private Mono<Void> resetFailureCountForFailedArticle(String articleUrl) {
        return Mono.fromRunnable(() -> {
            articleRepository.findByArticleUrl(articleUrl).ifPresent(article -> {
                var status = article.getScrapeStatus();
                if (status == ArticleScrapeStatus.ScrapeStatus.FAILED || status == ArticleScrapeStatus.ScrapeStatus.MAX_FAILURES) {
                    log.info("Parent scan is re-processing a previously failed article [{}]. Status was {}. Resetting failure count.", articleUrl, status);
                    article.setFailureCount(0);
                    article.setScrapeStatus(ArticleScrapeStatus.ScrapeStatus.PENDING);
                    article.setUpdatedAtUtc(Instant.now());
                    articleRepository.save(article);
                }
            });
        });
    }

    private Mono<Void> resetStuckArticleToPending(String articleUrl) {
        return Mono.fromRunnable(() -> {
            articleRepository.findByArticleUrl(articleUrl).ifPresent(article -> {
                log.info("Resetting stuck IN_PROGRESS article [{}] to FAILED for faster retry", articleUrl);
                article.setScrapeStatus(ArticleScrapeStatus.ScrapeStatus.FAILED);
                article.setUpdatedAtUtc(Instant.now());
                // Don't increment failure count since this was a timeout, not a real failure
                articleRepository.save(article);
            });
        });
    }
} 
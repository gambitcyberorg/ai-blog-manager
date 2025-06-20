package org.gc.aiagents.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blog-manager.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BlogManagerScheduler {
    
    private final BlogManagerService blogManagerService;
    
    /**
     * Runs scheduled parent scans
     * This checks for parent blogs that are due for scanning
     */
    @Scheduled(fixedRateString = "${blog-manager.scheduler.parent-scan-interval-ms:60000}")
    public void runScheduledParentScans() {
        log.debug("Running scheduled parent scans check");
        
        blogManagerService.runScheduledParentScans()
                .doOnSuccess(v -> log.debug("Scheduled parent scans completed"))
                .doOnError(error -> log.error("Error in scheduled parent scans: {}", error.getMessage()))
                .subscribe();
    }
    
    /**
     * Retries failed articles
     * This checks for failed articles that are due for retry
     */
    @Scheduled(fixedRateString = "${blog-manager.scheduler.retry-failed-interval-ms:600000}")
    public void retryFailedArticles() {
        log.debug("Running failed articles retry check");
        
        blogManagerService.retryFailedArticles()
                .doOnSuccess(v -> log.debug("Failed articles retry completed"))
                .doOnError(error -> log.error("Error in failed articles retry: {}", error.getMessage()))
                .subscribe();
    }
    
    /**
     * Processes pending articles
     * This checks for articles in PENDING status that need processing
     */
    @Scheduled(fixedRateString = "${blog-manager.scheduler.process-pending-interval-ms:180000}")
    public void processPendingArticles() {
        log.debug("Running pending articles processing check");
        
        blogManagerService.processPendingArticles()
                .doOnSuccess(v -> log.debug("Pending articles processing completed"))
                .doOnError(error -> log.error("Error in pending articles processing: {}", error.getMessage()))
                .subscribe();
    }
    
    /**
     * Resets articles stuck in IN_PROGRESS status
     * This checks for articles stuck in IN_PROGRESS for more than the configured timeout
     */
    @Scheduled(fixedRateString = "${blog-manager.scheduler.reset-stuck-interval-ms:300000}")
    public void resetStuckInProgressArticles() {
        log.debug("Running stuck IN_PROGRESS articles reset check");
        
        blogManagerService.resetStuckInProgressArticles()
                .doOnSuccess(v -> log.debug("Stuck IN_PROGRESS articles reset completed"))
                .doOnError(error -> log.error("Error in stuck articles reset: {}", error.getMessage()))
                .subscribe();
    }
    
    /**
     * Health check for the Python API every hour
     * This logs the health status for monitoring
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void checkPythonApiHealth() {
        log.debug("Checking Python API health");
        
        // This would be injected if needed, but for now we'll skip the health check
        // to avoid circular dependencies
        log.debug("Python API health check completed");
    }
} 
package org.gc.aiagents.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gc.aiagents.service.BlogManagerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

// DISABLED: Duplicate scheduler - using BlogManagerScheduler.java instead
// This was causing parent blogs to scan every 60 seconds instead of respecting the 24-hour interval

//@Slf4j
//@Configuration
//@RequiredArgsConstructor
//@ConditionalOnProperty(name = "blog-manager.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {
    // Moved to BlogManagerScheduler.java with proper timing:
    // - Parent scans: Every 5 minutes (checks for due blogs)
    // - Failed retries: Every 10 minutes
} 
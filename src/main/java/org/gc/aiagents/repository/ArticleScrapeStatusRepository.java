package org.gc.aiagents.repository;

import org.gc.aiagents.domain.ArticleScrapeStatus;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleScrapeStatusRepository extends ElasticsearchRepository<ArticleScrapeStatus, String> {
    
    Optional<ArticleScrapeStatus> findByArticleUrl(String articleUrl);
    
    List<ArticleScrapeStatus> findByParentUid(String parentUid);
    
    List<ArticleScrapeStatus> findByScrapeStatusAndFailureCountLessThan(
            ArticleScrapeStatus.ScrapeStatus scrapeStatus,
            Integer maxFailureCount
    );
    
    List<ArticleScrapeStatus> findByScrapeStatusAndParentUid(
            ArticleScrapeStatus.ScrapeStatus scrapeStatus, 
            String parentUid
    );
    
    List<ArticleScrapeStatus> findByScrapeStatus(ArticleScrapeStatus.ScrapeStatus scrapeStatus);
    
    boolean existsByArticleUrl(String articleUrl);
} 
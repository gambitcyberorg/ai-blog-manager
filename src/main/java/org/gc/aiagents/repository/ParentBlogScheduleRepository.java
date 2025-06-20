package org.gc.aiagents.repository;

import org.gc.aiagents.domain.ParentBlogSchedule;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParentBlogScheduleRepository extends ElasticsearchRepository<ParentBlogSchedule, String> {
    
    Optional<ParentBlogSchedule> findByParentUrl(String parentUrl);
    
    List<ParentBlogSchedule> findByNextScanDatetimeUtcLessThanEqual(Instant currentTime);
    
    List<ParentBlogSchedule> findAllByOrderByCreatedAtUtcDesc();
} 
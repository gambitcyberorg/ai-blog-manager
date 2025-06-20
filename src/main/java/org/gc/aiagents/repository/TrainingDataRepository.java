package org.gc.aiagents.repository;

import org.gc.aiagents.domain.TrainingData;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.Instant;
import java.util.List;

public interface TrainingDataRepository extends ElasticsearchRepository<TrainingData, String> {
    List<TrainingData> findByCreatedAfter(Instant after);
} 
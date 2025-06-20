package org.gc.aiagents.repository;

import org.gc.aiagents.domain.ValidationData;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ValidationDataRepository extends ElasticsearchRepository<ValidationData, String> {
} 
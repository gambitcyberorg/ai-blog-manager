package org.gc.aiagents.repository.es;

import org.gc.aiagents.domain.es.ThreatIntelDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThreatIntelRepository extends ElasticsearchRepository<ThreatIntelDoc, String> {
} 
package org.gc.aiagents.repository;

import com.gambitcyber.datamodel.backend.IndicatorEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndicatorRepository extends ElasticsearchRepository<IndicatorEntity, String > {
}

package org.gc.aiagents.repository;


import com.gambitcyber.datamodel.backend.ConfigEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigRepository extends ElasticsearchRepository<ConfigEntity, String> {

    List<ConfigEntity> findAllByEnvironmentAndTenantId(String environment, String tenantId);

    ConfigEntity findDistinctFirstByEnvironmentAndTenantIdAndApplicationKey(String environment, String tenantId, String applicationKey);

    List<ConfigEntity> findAllByEnvironmentAndTenantIdAndApplicationConfig(String environment, String tenantId, String applicationType);
}

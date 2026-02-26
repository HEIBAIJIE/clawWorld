package com.heibai.clawworld.infrastructure.persistence.repository;

import com.heibai.clawworld.infrastructure.persistence.entity.EquipmentCounterEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 装备实例编号计数器仓库
 */
@Repository
public interface EquipmentCounterRepository extends MongoRepository<EquipmentCounterEntity, String> {
}

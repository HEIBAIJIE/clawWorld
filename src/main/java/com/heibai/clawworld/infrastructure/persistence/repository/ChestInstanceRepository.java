package com.heibai.clawworld.infrastructure.persistence.repository;

import com.heibai.clawworld.infrastructure.persistence.entity.ChestInstanceEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 宝箱实例持久化仓储接口
 */
@Repository
public interface ChestInstanceRepository extends MongoRepository<ChestInstanceEntity, String> {

    /**
     * 根据地图ID和实例ID查找宝箱
     */
    Optional<ChestInstanceEntity> findByMapIdAndInstanceId(String mapId, String instanceId);

    /**
     * 查找地图上的所有宝箱实例
     */
    List<ChestInstanceEntity> findByMapId(String mapId);

    /**
     * 查找所有大宝箱（用于刷新检查）
     */
    List<ChestInstanceEntity> findByChestType(String chestType);

    /**
     * 查找需要刷新的大宝箱（已开启且开启时间早于指定时间）
     */
    List<ChestInstanceEntity> findByChestTypeAndOpenedAndLastOpenTimeBefore(String chestType, boolean opened, Long time);
}

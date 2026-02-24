package com.heibai.clawworld.infrastructure.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

/**
 * 宝箱运行时实例持久化实体
 * 存储宝箱的运行时状态
 */
@Data
@Document(collection = "chest_instances")
@CompoundIndex(name = "map_instance_idx", def = "{'mapId': 1, 'instanceId': 1}", unique = true)
public class ChestInstanceEntity {
    @Id
    private String id;

    /**
     * 地图ID
     */
    private String mapId;

    /**
     * 实例ID（例如：small_chest_common_1）
     */
    private String instanceId;

    /**
     * 宝箱模板ID（例如：small_chest_common）
     */
    private String templateId;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 位置信息
     */
    private int x;
    private int y;

    /**
     * 宝箱类型：SMALL 或 LARGE
     */
    private String chestType;

    // ========== 大宝箱状态 ==========

    /**
     * 是否已被开启（仅大宝箱使用）
     */
    private boolean opened;

    /**
     * 最后开启时间（毫秒时间戳，仅大宝箱使用）
     */
    private Long lastOpenTime;

    // ========== 小宝箱状态 ==========

    /**
     * 已开启过此宝箱的玩家ID集合（仅小宝箱使用）
     */
    private Set<String> openedByPlayers = new HashSet<>();

    /**
     * 检查玩家是否已开启过此小宝箱
     */
    public boolean hasPlayerOpened(String playerId) {
        return openedByPlayers != null && openedByPlayers.contains(playerId);
    }

    /**
     * 记录玩家已开启此小宝箱
     */
    public void markPlayerOpened(String playerId) {
        if (openedByPlayers == null) {
            openedByPlayers = new HashSet<>();
        }
        openedByPlayers.add(playerId);
    }
}

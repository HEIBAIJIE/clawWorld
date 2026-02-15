package com.heibai.clawworld.config.map;

import lombok.Data;

/**
 * 地图实体配置 - 从CSV读取
 * entityId: 模板ID（如 goblin, wolf）
 * instanceId: 实例ID（如 goblin_1, goblin_2），用于区分同一模板的不同实例
 */
@Data
public class MapEntityConfig {
    private String mapId;
    private int x;
    private int y;
    private String entityType;
    private String entityId;  // 模板ID
    private String instanceId;  // 实例ID，可选
}

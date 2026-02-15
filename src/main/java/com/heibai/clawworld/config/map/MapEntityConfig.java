package com.heibai.clawworld.config.map;

import lombok.Data;

/**
 * 地图实体配置 - 从CSV读取
 */
@Data
public class MapEntityConfig {
    private String mapId;
    private int x;
    private int y;
    private String entityType;
    private String entityId;
}

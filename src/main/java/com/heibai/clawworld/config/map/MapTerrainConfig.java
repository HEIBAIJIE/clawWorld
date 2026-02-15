package com.heibai.clawworld.config.map;

import lombok.Data;

/**
 * 地图地形配置 - 从CSV读取，用于覆盖默认地形
 */
@Data
public class MapTerrainConfig {
    private String mapId;
    private int x;
    private int y;
    private String terrainTypes;
}

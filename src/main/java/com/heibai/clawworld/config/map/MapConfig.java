package com.heibai.clawworld.config.map;

import lombok.Data;

/**
 * 地图配置 - 从CSV读取的扁平化配置
 */
@Data
public class MapConfig {
    private String id;
    private String name;
    private String description;
    private int width;
    private int height;
    private boolean isSafe;
    private Integer recommendedLevel;
    private String defaultTerrain;
}

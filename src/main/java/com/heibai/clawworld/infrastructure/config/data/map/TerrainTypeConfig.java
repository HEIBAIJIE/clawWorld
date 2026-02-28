package com.heibai.clawworld.infrastructure.config.data.map;

import lombok.Data;

/**
 * 地形类型配置 - 从CSV读取
 */
@Data
public class TerrainTypeConfig {
    private String id;
    private String name;

    // GUI资源
    private String icon;
}

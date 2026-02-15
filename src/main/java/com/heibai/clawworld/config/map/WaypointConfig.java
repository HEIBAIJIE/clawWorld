package com.heibai.clawworld.config.map;

import lombok.Data;

/**
 * 传送点配置 - 从CSV读取
 */
@Data
public class WaypointConfig {
    private String id;
    private String name;
    private String description;
    private String targetMapId;
    private int targetX;
    private int targetY;
}

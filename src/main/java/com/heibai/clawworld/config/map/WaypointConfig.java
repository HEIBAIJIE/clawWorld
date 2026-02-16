package com.heibai.clawworld.config.map;

import lombok.Data;

import java.util.List;

/**
 * 传送点配置 - 从CSV读取
 */
@Data
public class WaypointConfig {
    private String id;
    private String mapId;  // 传送点所在的地图ID
    private String name;
    private String description;
    private int x;  // 传送点在地图上的X坐标
    private int y;  // 传送点在地图上的Y坐标
    private List<String> connectedWaypointIds;  // 可以传送到的其他传送点ID列表
}

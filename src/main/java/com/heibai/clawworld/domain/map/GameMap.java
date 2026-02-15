package com.heibai.clawworld.domain.map;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 地图领域对象 - 运行时使用
 */
@Data
@Document(collection = "maps")
public class GameMap {
    @Id
    private String id;
    private String name;
    private String description;
    private int width;
    private int height;
    private boolean isSafe;
    private Integer recommendedLevel;

    // 地形数据：二维数组，每个格子存储地形类型列表
    private List<List<TerrainCell>> terrain;

    // 地图上的实体列表
    private List<MapEntity> entities;

    @Data
    public static class TerrainCell {
        private List<String> terrainTypes;
    }
}

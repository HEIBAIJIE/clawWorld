package com.heibai.clawworld.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "map_configs")
public class MapConfig {
    @Id
    private String id;
    private String name;
    private String description;
    private int width;
    private int height;
    private boolean isSafe;
    private Integer recommendedLevel;
    private List<TerrainCell> terrain;
    private List<EntityPlacement> entities;
    private List<String> connectedMaps;

    @Data
    public static class TerrainCell {
        private int x;
        private int y;
        private List<String> terrainTypes;
    }

    @Data
    public static class EntityPlacement {
        private int x;
        private int y;
        private String entityType;
        private String entityId;
    }
}

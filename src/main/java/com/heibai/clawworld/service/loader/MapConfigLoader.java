package com.heibai.clawworld.service.loader;

import com.heibai.clawworld.config.map.*;
import com.heibai.clawworld.util.CsvReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地图配置加载器
 */
@Component
@RequiredArgsConstructor
public class MapConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(MapConfigLoader.class);
    private final CsvReader csvReader;
    private final ResourceLoader resourceLoader;

    private final Map<String, MapConfig> mapConfigs = new ConcurrentHashMap<>();
    private final Map<String, WaypointConfig> waypointConfigs = new ConcurrentHashMap<>();
    private final List<MapTerrainConfig> mapTerrainConfigs = new ArrayList<>();
    private final List<MapEntityConfig> mapEntityConfigs = new ArrayList<>();

    public void loadMaps() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/maps.csv");
            if (!resource.exists()) {
                log.warn("maps.csv not found, skipping");
                return;
            }

            List<MapConfig> maps = csvReader.readCsv(resource.getInputStream(), record -> {
                MapConfig map = new MapConfig();
                map.setId(csvReader.getString(record, "id"));
                map.setName(csvReader.getString(record, "name"));
                map.setDescription(csvReader.getString(record, "description"));
                map.setWidth(csvReader.getInt(record, "width"));
                map.setHeight(csvReader.getInt(record, "height"));
                map.setSafe(csvReader.getBoolean(record, "isSafe"));
                map.setRecommendedLevel(csvReader.getIntOrNull(record, "recommendedLevel"));
                map.setDefaultTerrain(csvReader.getString(record, "defaultTerrain"));
                return map;
            });

            mapConfigs.clear();
            maps.forEach(map -> mapConfigs.put(map.getId(), map));
            log.info("Loaded {} maps", maps.size());
        } catch (IOException e) {
            log.error("Error loading maps.csv", e);
        }
    }

    public void loadWaypoints() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/waypoints.csv");
            if (!resource.exists()) {
                log.warn("waypoints.csv not found, skipping");
                return;
            }

            List<WaypointConfig> waypoints = csvReader.readCsv(resource.getInputStream(), record -> {
                WaypointConfig waypoint = new WaypointConfig();
                waypoint.setId(csvReader.getString(record, "id"));
                waypoint.setMapId(csvReader.getString(record, "mapId"));
                waypoint.setName(csvReader.getString(record, "name"));
                waypoint.setDescription(csvReader.getString(record, "description"));
                waypoint.setX(csvReader.getInt(record, "x"));
                waypoint.setY(csvReader.getInt(record, "y"));

                // 解析连接的传送点ID列表（分号分隔）
                String connectedIds = csvReader.getStringOrNull(record, "connectedWaypointIds");
                if (connectedIds != null && !connectedIds.trim().isEmpty()) {
                    waypoint.setConnectedWaypointIds(
                        java.util.Arrays.stream(connectedIds.split(";"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toList())
                    );
                } else {
                    waypoint.setConnectedWaypointIds(new java.util.ArrayList<>());
                }
                return waypoint;
            });

            waypointConfigs.clear();
            waypoints.forEach(wp -> waypointConfigs.put(wp.getId(), wp));
            log.info("Loaded {} waypoints", waypoints.size());
        } catch (IOException e) {
            log.error("Error loading waypoints.csv", e);
        }
    }

    public void loadMapTerrain() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/map_terrain.csv");
            if (!resource.exists()) {
                log.warn("map_terrain.csv not found, skipping");
                return;
            }

            List<MapTerrainConfig> terrains = csvReader.readCsv(resource.getInputStream(), record -> {
                MapTerrainConfig terrain = new MapTerrainConfig();
                terrain.setMapId(csvReader.getString(record, "mapId"));
                terrain.setX(csvReader.getInt(record, "x"));
                terrain.setY(csvReader.getInt(record, "y"));
                terrain.setTerrainTypes(csvReader.getString(record, "terrainTypes"));
                return terrain;
            });

            mapTerrainConfigs.clear();
            mapTerrainConfigs.addAll(terrains);
            log.info("Loaded {} map terrain entries", terrains.size());
        } catch (IOException e) {
            log.error("Error loading map_terrain.csv", e);
        }
    }

    public void loadMapEntities() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/map_entities.csv");
            if (!resource.exists()) {
                log.warn("map_entities.csv not found, skipping");
                return;
            }

            List<MapEntityConfig> entities = csvReader.readCsv(resource.getInputStream(), record -> {
                MapEntityConfig entity = new MapEntityConfig();
                entity.setMapId(csvReader.getString(record, "mapId"));
                entity.setX(csvReader.getInt(record, "x"));
                entity.setY(csvReader.getInt(record, "y"));
                entity.setEntityType(csvReader.getString(record, "entityType"));
                entity.setEntityId(csvReader.getString(record, "entityId"));
                entity.setInstanceId(csvReader.getStringOrNull(record, "instanceId"));
                return entity;
            });

            mapEntityConfigs.clear();
            mapEntityConfigs.addAll(entities);
            log.info("Loaded {} map entity entries", entities.size());
        } catch (IOException e) {
            log.error("Error loading map_entities.csv", e);
        }
    }

    public MapConfig getMap(String id) {
        return mapConfigs.get(id);
    }

    public WaypointConfig getWaypoint(String id) {
        return waypointConfigs.get(id);
    }

    public Map<String, MapConfig> getAllMaps() {
        return mapConfigs;
    }

    public Map<String, WaypointConfig> getAllWaypoints() {
        return waypointConfigs;
    }

    public List<MapTerrainConfig> getMapTerrain(String mapId) {
        return mapTerrainConfigs.stream()
                .filter(terrain -> terrain.getMapId().equals(mapId))
                .toList();
    }

    public List<MapEntityConfig> getMapEntities(String mapId) {
        return mapEntityConfigs.stream()
                .filter(entity -> entity.getMapId().equals(mapId))
                .toList();
    }
}

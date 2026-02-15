package com.heibai.clawworld.service;

import com.heibai.clawworld.config.map.MapConfig;
import com.heibai.clawworld.config.map.MapEntityConfig;
import com.heibai.clawworld.config.map.MapTerrainConfig;
import com.heibai.clawworld.domain.character.Enemy;
import com.heibai.clawworld.domain.character.Npc;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.domain.map.Waypoint;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 地图初始化服务
 * 负责从配置文件创建运行时的地图实例
 */
@Service
@RequiredArgsConstructor
public class MapInitializationService {

    private static final Logger log = LoggerFactory.getLogger(MapInitializationService.class);

    private final ConfigDataManager configDataManager;
    private final EntityFactory entityFactory;

    // 运行时地图缓存 - 实际游戏中应该存储在数据库或分布式缓存中
    private final Map<String, GameMap> runtimeMaps = new HashMap<>();

    @PostConstruct
    public void initializeMaps() {
        log.info("Initializing game maps...");

        Collection<MapConfig> mapConfigs = configDataManager.getAllMaps();
        for (MapConfig mapConfig : mapConfigs) {
            try {
                GameMap gameMap = createMapInstance(mapConfig);
                runtimeMaps.put(gameMap.getId(), gameMap);
                log.info("Initialized map: {} with {} entities",
                        gameMap.getName(), gameMap.getEntities().size());
            } catch (Exception e) {
                log.error("Failed to initialize map: {}", mapConfig.getId(), e);
            }
        }

        log.info("Map initialization complete. Total maps: {}", runtimeMaps.size());
    }

    /**
     * 从配置创建地图实例
     */
    private GameMap createMapInstance(MapConfig config) {
        GameMap map = new GameMap();
        map.setId(config.getId());
        map.setName(config.getName());
        map.setDescription(config.getDescription());
        map.setWidth(config.getWidth());
        map.setHeight(config.getHeight());
        map.setSafe(config.isSafe());
        map.setRecommendedLevel(config.getRecommendedLevel());

        // 初始化地形
        map.setTerrain(initializeTerrain(config));

        // 初始化实体
        map.setEntities(initializeEntities(config.getId()));

        return map;
    }

    /**
     * 初始化地形数据
     */
    private List<List<GameMap.TerrainCell>> initializeTerrain(MapConfig config) {
        List<List<GameMap.TerrainCell>> terrain = new ArrayList<>();

        // 创建二维地形数组
        for (int y = 0; y < config.getHeight(); y++) {
            List<GameMap.TerrainCell> row = new ArrayList<>();
            for (int x = 0; x < config.getWidth(); x++) {
                GameMap.TerrainCell cell = new GameMap.TerrainCell();
                // 默认使用地图的默认地形
                cell.setTerrainTypes(Arrays.asList(config.getDefaultTerrain()));
                row.add(cell);
            }
            terrain.add(row);
        }

        // 应用特殊地形配置
        List<MapTerrainConfig> terrainConfigs = configDataManager.getMapTerrain(config.getId());
        for (MapTerrainConfig tc : terrainConfigs) {
            if (tc.getX() >= 0 && tc.getX() < config.getWidth() &&
                tc.getY() >= 0 && tc.getY() < config.getHeight()) {

                GameMap.TerrainCell cell = terrain.get(tc.getY()).get(tc.getX());
                List<String> terrainTypes = Arrays.stream(tc.getTerrainTypes().split(";"))
                        .map(String::trim)
                        .toList();
                cell.setTerrainTypes(terrainTypes);
            }
        }

        return terrain;
    }

    /**
     * 初始化地图实体
     */
    private List<MapEntity> initializeEntities(String mapId) {
        List<MapEntity> entities = new ArrayList<>();

        List<MapEntityConfig> entityConfigs = configDataManager.getMapEntities(mapId);
        for (MapEntityConfig config : entityConfigs) {
            MapEntity entity = createEntityInstance(config, mapId);
            if (entity != null) {
                entities.add(entity);
            }
        }

        return entities;
    }

    /**
     * 根据配置创建实体实例
     */
    private MapEntity createEntityInstance(MapEntityConfig config, String mapId) {
        return switch (config.getEntityType().toUpperCase()) {
            case "ENEMY" -> entityFactory.createEnemyInstance(config, mapId);
            case "NPC" -> entityFactory.createNpcInstance(config, mapId);
            case "WAYPOINT" -> entityFactory.createWaypointInstance(config, mapId);
            default -> {
                log.warn("Unknown entity type: {} for entity: {}",
                        config.getEntityType(), config.getEntityId());
                yield null;
            }
        };
    }

    /**
     * 获取运行时地图实例
     */
    public GameMap getMap(String mapId) {
        return runtimeMaps.get(mapId);
    }

    /**
     * 获取所有运行时地图
     */
    public Collection<GameMap> getAllMaps() {
        return runtimeMaps.values();
    }

    /**
     * 重新加载指定地图
     */
    public void reloadMap(String mapId) {
        MapConfig config = configDataManager.getMap(mapId);
        if (config != null) {
            GameMap gameMap = createMapInstance(config);
            runtimeMaps.put(mapId, gameMap);
            log.info("Reloaded map: {}", mapId);
        }
    }

    /**
     * 重新加载所有地图
     */
    public void reloadAllMaps() {
        runtimeMaps.clear();
        initializeMaps();
    }
}

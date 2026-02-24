package com.heibai.clawworld.infrastructure.factory;

import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.ChestConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapEntityConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapTerrainConfig;
import com.heibai.clawworld.domain.character.Enemy;
import com.heibai.clawworld.domain.character.Npc;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.domain.map.Waypoint;
import com.heibai.clawworld.infrastructure.persistence.entity.ChestInstanceEntity;
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
    private final com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository enemyInstanceRepository;
    private final com.heibai.clawworld.infrastructure.persistence.repository.NpcShopInstanceRepository npcShopInstanceRepository;
    private final com.heibai.clawworld.infrastructure.persistence.repository.ChestInstanceRepository chestInstanceRepository;

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

                // 同步敌人实例到数据库
                syncEnemiesToDatabase(mapConfig.getId(), gameMap.getEntities());

                // 同步NPC实例到数据库
                syncNpcsToDatabase(mapConfig.getId(), gameMap.getEntities());

                // 同步宝箱实例到数据库
                syncChestsToDatabase(mapConfig.getId());

                log.info("Initialized map: {} with {} entities",
                        gameMap.getName(), gameMap.getEntities().size());
            } catch (Exception e) {
                log.error("Failed to initialize map: {}", mapConfig.getId(), e);
            }
        }

        log.info("Map initialization complete. Total maps: {}", runtimeMaps.size());
    }

    /**
     * 同步敌人实例到数据库
     * 如果数据库中已存在则保留现有状态，否则创建新实例
     */
    private void syncEnemiesToDatabase(String mapId, List<MapEntity> entities) {
        for (MapEntity entity : entities) {
            if (entity instanceof com.heibai.clawworld.domain.character.Enemy enemy) {
                // 检查数据库中是否已存在该敌人实例
                var existingOpt = enemyInstanceRepository.findByMapIdAndInstanceId(mapId, enemy.getId());
                if (existingOpt.isEmpty()) {
                    // 创建新的敌人实例实体
                    var enemyEntity = new com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity();
                    enemyEntity.setMapId(mapId);
                    enemyEntity.setInstanceId(enemy.getId());
                    enemyEntity.setTemplateId(extractTemplateId(enemy.getId()));
                    enemyEntity.setDisplayName(enemy.getName());
                    enemyEntity.setCurrentHealth(enemy.getCurrentHealth());
                    enemyEntity.setCurrentMana(enemy.getCurrentMana());
                    enemyEntity.setDead(false);
                    enemyEntity.setInCombat(false);
                    enemyEntity.setX(enemy.getX());
                    enemyEntity.setY(enemy.getY());

                    enemyInstanceRepository.save(enemyEntity);
                    log.debug("Created enemy instance in database: {} on map {}", enemy.getName(), mapId);
                }
            }
        }
    }

    /**
     * 从实例ID提取模板ID
     * 例如: goblin_1 -> goblin, wolf_2 -> wolf
     */
    private String extractTemplateId(String instanceId) {
        int lastUnderscore = instanceId.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String suffix = instanceId.substring(lastUnderscore + 1);
            try {
                Integer.parseInt(suffix);
                return instanceId.substring(0, lastUnderscore);
            } catch (NumberFormatException e) {
                // 不是数字后缀，返回原始ID
            }
        }
        return instanceId;
    }

    /**
     * 同步NPC实例到数据库
     */
    private void syncNpcsToDatabase(String mapId, List<MapEntity> entities) {
        for (MapEntity entity : entities) {
            if (entity instanceof com.heibai.clawworld.domain.character.Npc npc) {
                // 检查数据库中是否已存在该NPC实例
                var existingOpt = npcShopInstanceRepository.findByNpcId(npc.getId());
                if (existingOpt.isEmpty()) {
                    // 获取NPC配置
                    var npcConfig = configDataManager.getNpc(npc.getId());
                    if (npcConfig != null && npcConfig.isHasShop()) {
                        // 创建新的NPC商店实例实体
                        var npcEntity = new com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity();
                        npcEntity.setNpcId(npc.getId());
                        npcEntity.setMapId(mapId);
                        npcEntity.setCurrentGold(npcConfig.getShopGold());
                        npcEntity.setLastRefreshTime(System.currentTimeMillis());

                        // 初始化商店物品
                        var shopItems = configDataManager.getNpcShopItems(npc.getId());
                        var itemDataList = new java.util.ArrayList<com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity.ShopItemData>();
                        for (var shopItem : shopItems) {
                            var itemData = new com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity.ShopItemData();
                            itemData.setItemId(shopItem.getItemId());
                            itemData.setMaxQuantity(shopItem.getQuantity());
                            itemData.setCurrentQuantity(shopItem.getQuantity());
                            itemDataList.add(itemData);
                        }
                        npcEntity.setItems(itemDataList);

                        npcShopInstanceRepository.save(npcEntity);
                        log.debug("Created NPC shop instance in database: {} on map {}", npc.getName(), mapId);
                    }
                }
            }
        }
    }

    /**
     * 同步宝箱实例到数据库
     */
    private void syncChestsToDatabase(String mapId) {
        List<MapEntityConfig> mapEntities = configDataManager.getMapEntities(mapId);

        for (MapEntityConfig entity : mapEntities) {
            if ("CHEST_SMALL".equals(entity.getEntityType()) || "CHEST_LARGE".equals(entity.getEntityType())) {
                String instanceId = entity.getInstanceId();
                if (instanceId == null || instanceId.isEmpty()) {
                    instanceId = entity.getEntityId() + "_" + entity.getX() + "_" + entity.getY();
                }

                // 检查是否已存在
                var existing = chestInstanceRepository.findByMapIdAndInstanceId(mapId, instanceId);
                if (existing.isPresent()) {
                    continue;
                }

                // 获取宝箱配置
                ChestConfig chestConfig = configDataManager.getChest(entity.getEntityId());
                if (chestConfig == null) {
                    log.warn("宝箱配置不存在: {}", entity.getEntityId());
                    continue;
                }

                // 创建宝箱实例
                ChestInstanceEntity chest = new ChestInstanceEntity();
                chest.setMapId(mapId);
                chest.setInstanceId(instanceId);
                chest.setTemplateId(entity.getEntityId());
                chest.setDisplayName(chestConfig.getName());
                chest.setX(entity.getX());
                chest.setY(entity.getY());
                chest.setChestType(chestConfig.getType());
                chest.setOpened(false);
                chest.setOpenedByPlayers(new HashSet<>());

                chestInstanceRepository.save(chest);
                log.debug("Created chest instance in database: {} on map {} at ({}, {})",
                        chestConfig.getName(), mapId, entity.getX(), entity.getY());
            }
        }
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

        // 应用特殊地形配置（矩形区域）
        List<MapTerrainConfig> terrainConfigs = configDataManager.getMapTerrain(config.getId());
        for (MapTerrainConfig tc : terrainConfigs) {
            // 遍历矩形区域内的所有格子
            int minX = Math.max(0, Math.min(tc.getX1(), tc.getX2()));
            int maxX = Math.min(config.getWidth() - 1, Math.max(tc.getX1(), tc.getX2()));
            int minY = Math.max(0, Math.min(tc.getY1(), tc.getY2()));
            int maxY = Math.min(config.getHeight() - 1, Math.max(tc.getY1(), tc.getY2()));

            List<String> terrainTypes = Arrays.stream(tc.getTerrainTypes().split(";"))
                    .map(String::trim)
                    .toList();

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    GameMap.TerrainCell cell = terrain.get(y).get(x);
                    cell.setTerrainTypes(terrainTypes);
                }
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
            case "CAMPFIRE" -> entityFactory.createCampfireInstance(config, mapId);
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

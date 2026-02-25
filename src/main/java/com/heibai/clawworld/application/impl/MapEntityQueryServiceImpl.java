package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.MapEntityQueryService;
import com.heibai.clawworld.domain.character.Enemy;
import com.heibai.clawworld.domain.character.Npc;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.Campfire;
import com.heibai.clawworld.domain.map.Chest;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.domain.map.Waypoint;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.EnemyConfig;
import com.heibai.clawworld.infrastructure.config.data.character.NpcConfig;
import com.heibai.clawworld.infrastructure.config.data.map.ChestConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapEntityConfig;
import com.heibai.clawworld.infrastructure.config.data.map.WaypointConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.ChestInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.ChestInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.NpcShopInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 地图实体查询服务实现
 */
@Service
@RequiredArgsConstructor
public class MapEntityQueryServiceImpl implements MapEntityQueryService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final EnemyInstanceRepository enemyInstanceRepository;
    private final NpcShopInstanceRepository npcShopInstanceRepository;
    private final ChestInstanceRepository chestInstanceRepository;
    private final ConfigDataManager configDataManager;

    @Override
    public List<MapEntity> getMapEntities(String mapId) {
        return getMapEntities(mapId, null);
    }

    @Override
    public List<MapEntity> getMapEntities(String mapId, String playerId) {
        List<MapEntity> entities = new ArrayList<>();

        // 1. 获取地图上的所有玩家
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
                .filter(p -> mapId.equals(p.getCurrentMapId()))
                .collect(Collectors.toList());
        for (PlayerEntity p : playersOnMap) {
            entities.add(playerMapper.toDomain(p));
        }

        // 2. 获取地图上的所有敌人
        List<EnemyInstanceEntity> enemies = enemyInstanceRepository.findByMapId(mapId);
        for (EnemyInstanceEntity enemy : enemies) {
            entities.add(convertToEnemy(enemy));
        }

        // 3. 获取地图上的所有NPC
        List<NpcShopInstanceEntity> npcs = npcShopInstanceRepository.findByMapId(mapId);
        for (NpcShopInstanceEntity npc : npcs) {
            MapEntity npcEntity = convertToNpc(npc);
            if (npcEntity != null) {
                entities.add(npcEntity);
            }
        }

        // 4. 获取地图上的传送点
        List<WaypointConfig> waypoints = configDataManager.getAllWaypoints().stream()
                .filter(w -> mapId.equals(w.getMapId()))
                .collect(Collectors.toList());
        for (WaypointConfig wp : waypoints) {
            entities.add(convertToWaypoint(wp));
        }

        // 5. 获取地图上的篝火等静态实体
        List<MapEntityConfig> mapEntityConfigs = configDataManager.getMapEntities(mapId);
        for (MapEntityConfig config : mapEntityConfigs) {
            if ("CAMPFIRE".equals(config.getEntityType())) {
                entities.add(convertToCampfire(config));
            }
        }

        // 6. 获取地图上的宝箱
        List<ChestInstanceEntity> chests = chestInstanceRepository.findByMapId(mapId);
        for (ChestInstanceEntity chest : chests) {
            entities.add(convertToChest(chest, playerId));
        }

        return entities;
    }

    @Override
    public List<MapEntity> getNearbyInteractableEntities(String playerId) {
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return new ArrayList<>();
        }

        PlayerEntity player = playerOpt.get();
        String mapId = player.getCurrentMapId();
        int px = player.getX();
        int py = player.getY();

        List<MapEntity> entities = new ArrayList<>();

        // 1. 周围玩家
        playerRepository.findAll().stream()
                .filter(p -> mapId.equals(p.getCurrentMapId()))
                .filter(p -> !p.getId().equals(playerId))
                .filter(p -> isInRange(px, py, p.getX(), p.getY()))
                .forEach(p -> entities.add(playerMapper.toDomain(p)));

        // 2. 周围敌人
        enemyInstanceRepository.findByMapId(mapId).stream()
                .filter(e -> isInRange(px, py, e.getX(), e.getY()))
                .forEach(e -> entities.add(convertToEnemy(e)));

        // 3. 周围NPC
        npcShopInstanceRepository.findByMapId(mapId).stream()
                .filter(n -> {
                    NpcConfig config = configDataManager.getNpc(n.getNpcId());
                    if (config == null) return false;
                    // 从 map_entities.csv 获取 NPC 位置
                    MapEntityConfig entityConfig = findMapEntityConfig(mapId, "NPC", n.getNpcId());
                    if (entityConfig == null) return false;
                    return isInRange(px, py, entityConfig.getX(), entityConfig.getY());
                })
                .forEach(n -> {
                    MapEntity npcEntity = convertToNpc(n);
                    if (npcEntity != null) {
                        entities.add(npcEntity);
                    }
                });

        // 4. 周围传送点
        configDataManager.getAllWaypoints().stream()
                .filter(w -> mapId.equals(w.getMapId()))
                .filter(w -> isInRange(px, py, w.getX(), w.getY()))
                .forEach(w -> entities.add(convertToWaypoint(w)));

        // 5. 周围篝火
        configDataManager.getMapEntities(mapId).stream()
                .filter(e -> "CAMPFIRE".equals(e.getEntityType()))
                .filter(e -> isInRange(px, py, e.getX(), e.getY()))
                .forEach(e -> entities.add(convertToCampfire(e)));

        // 6. 周围宝箱
        chestInstanceRepository.findByMapId(mapId).stream()
                .filter(c -> isInRange(px, py, c.getX(), c.getY()))
                .forEach(c -> entities.add(convertToChest(c, playerId)));

        return entities;
    }

    @Override
    public List<MapEntity> getEntitiesByType(String mapId, String entityType) {
        return getMapEntities(mapId).stream()
                .filter(e -> entityType.equals(e.getEntityType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MapEntity> getEntitiesAtPosition(String mapId, int x, int y) {
        return getMapEntities(mapId).stream()
                .filter(e -> e.getX() == x && e.getY() == y)
                .collect(Collectors.toList());
    }

    /**
     * 检查两个位置是否在九宫格范围内
     */
    private boolean isInRange(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        return dx <= 1 && dy <= 1;
    }

    /**
     * 将敌人实例转换为领域对象
     */
    private Enemy convertToEnemy(EnemyInstanceEntity entity) {
        EnemyConfig config = configDataManager.getEnemy(entity.getTemplateId());
        Enemy enemy = new Enemy();
        enemy.setId(entity.getInstanceId());
        enemy.setName(entity.getDisplayName());
        enemy.setMapId(entity.getMapId());
        enemy.setX(entity.getX());
        enemy.setY(entity.getY());

        if (config != null) {
            enemy.setDescription(config.getDescription());
            enemy.setLevel(config.getLevel());
            enemy.setTier(Enemy.EnemyTier.valueOf(config.getTier()));
            enemy.setMaxHealth(config.getHealth());
            enemy.setCurrentHealth(entity.getCurrentHealth());
            enemy.setMaxMana(config.getMana());
            enemy.setCurrentMana(entity.getCurrentMana());
            enemy.setPhysicalAttack(config.getPhysicalAttack());
            enemy.setPhysicalDefense(config.getPhysicalDefense());
            enemy.setMagicAttack(config.getMagicAttack());
            enemy.setMagicDefense(config.getMagicDefense());
            enemy.setSpeed(config.getSpeed());
            enemy.setCritRate(config.getCritRate());
            enemy.setCritDamage(config.getCritDamage());
            enemy.setHitRate(config.getHitRate());
            enemy.setDodgeRate(config.getDodgeRate());
            enemy.setExpMin(config.getExpMin());
            enemy.setExpMax(config.getExpMax());
            enemy.setGoldMin(config.getGoldMin());
            enemy.setGoldMax(config.getGoldMax());
            enemy.setRespawnSeconds(config.getRespawnSeconds());
            enemy.setFaction("ENEMY_" + config.getId());
        }

        enemy.setLastDeathTime(entity.getLastDeathTime());
        enemy.setInCombat(entity.isInCombat());
        enemy.setCombatId(entity.getCombatId());
        enemy.setDead(entity.isDead());

        return enemy;
    }

    /**
     * 将NPC实例转换为领域对象
     */
    private Npc convertToNpc(NpcShopInstanceEntity entity) {
        NpcConfig config = configDataManager.getNpc(entity.getNpcId());
        if (config == null) {
            return null;
        }

        // 从 map_entities.csv 获取 NPC 位置
        MapEntityConfig entityConfig = findMapEntityConfig(entity.getMapId(), "NPC", entity.getNpcId());

        Npc npc = new Npc();
        npc.setId(entity.getNpcId());
        npc.setName(config.getName());
        npc.setDescription(config.getDescription());
        npc.setMapId(entity.getMapId());
        npc.setHasShop(config.isHasShop());
        npc.setHasDialogue(config.isHasDialogue());
        npc.setFaction("FRIENDLY");

        // 设置位置
        if (entityConfig != null) {
            npc.setX(entityConfig.getX());
            npc.setY(entityConfig.getY());
        }

        // 解析对话内容
        if (config.isHasDialogue() && config.getDialogues() != null && !config.getDialogues().isEmpty()) {
            List<String> dialogueList = new ArrayList<>();
            for (String line : config.getDialogues().split("\\|")) {
                dialogueList.add(line.trim());
            }
            npc.setDialogues(dialogueList);
        }

        // 设置商店信息
        if (config.isHasShop()) {
            Npc.Shop shop = new Npc.Shop();
            shop.setGold(entity.getCurrentGold());
            shop.setRefreshSeconds(config.getShopRefreshSeconds());
            shop.setPriceMultiplier(config.getPriceMultiplier());
            shop.setLastRefreshTime(entity.getLastRefreshTime());
            npc.setShop(shop);
        }

        return npc;
    }

    /**
     * 将传送点配置转换为领域对象
     */
    private Waypoint convertToWaypoint(WaypointConfig config) {
        Waypoint waypoint = new Waypoint();
        waypoint.setId(config.getId());
        waypoint.setName(config.getName());
        waypoint.setDescription(config.getDescription());
        waypoint.setMapId(config.getMapId());
        waypoint.setX(config.getX());
        waypoint.setY(config.getY());
        waypoint.setConnectedWaypointIds(config.getConnectedWaypointIds());

        // 生成连接的传送点显示名称
        if (config.getConnectedWaypointIds() != null) {
            List<String> displayNames = new ArrayList<>();
            for (String wpId : config.getConnectedWaypointIds()) {
                WaypointConfig connectedWp = configDataManager.getWaypoint(wpId);
                if (connectedWp != null) {
                    var mapConfig = configDataManager.getMap(connectedWp.getMapId());
                    String mapName = mapConfig != null ? mapConfig.getName() : connectedWp.getMapId();
                    displayNames.add(mapName + "·" + connectedWp.getName());
                }
            }
            waypoint.setConnectedWaypointDisplayNames(displayNames);
        }

        return waypoint;
    }

    /**
     * 将篝火配置转换为领域对象
     */
    private Campfire convertToCampfire(MapEntityConfig config) {
        Campfire campfire = new Campfire();
        campfire.setId(config.getEntityId());
        campfire.setName("篝火");
        campfire.setDescription("可以在这里休息恢复生命和法力");
        campfire.setMapId(config.getMapId());
        campfire.setX(config.getX());
        campfire.setY(config.getY());
        return campfire;
    }

    /**
     * 将宝箱实例转换为领域对象
     */
    private Chest convertToChest(ChestInstanceEntity entity) {
        return convertToChest(entity, null);
    }

    /**
     * 将宝箱实例转换为领域对象（带玩家ID，用于判断小宝箱是否已被当前玩家开启）
     */
    private Chest convertToChest(ChestInstanceEntity entity, String playerId) {
        ChestConfig config = configDataManager.getChest(entity.getTemplateId());

        Chest chest = new Chest();
        chest.setId(entity.getInstanceId());
        chest.setMapId(entity.getMapId());
        chest.setX(entity.getX());
        chest.setY(entity.getY());
        chest.setChestConfigId(entity.getTemplateId());

        if (config != null) {
            chest.setName(config.getName());
            chest.setDescription(config.getDescription());
            chest.setChestType("SMALL".equals(config.getType()) ? Chest.ChestType.SMALL : Chest.ChestType.LARGE);
            chest.setRespawnSeconds(config.getRespawnSeconds());
        } else {
            chest.setName(entity.getDisplayName());
            chest.setDescription("一个宝箱");
            chest.setChestType("SMALL".equals(entity.getChestType()) ? Chest.ChestType.SMALL : Chest.ChestType.LARGE);
        }

        chest.setOpened(entity.isOpened());
        chest.setLastOpenTime(entity.getLastOpenTime() != null ? entity.getLastOpenTime() : 0);

        // 设置当前玩家是否已开启（小宝箱）
        if (playerId != null && "SMALL".equals(entity.getChestType())) {
            chest.setOpenedByCurrentPlayer(entity.hasPlayerOpened(playerId));
        }

        return chest;
    }

    /**
     * 查找地图实体配置
     */
    private MapEntityConfig findMapEntityConfig(String mapId, String entityType, String entityId) {
        return configDataManager.getMapEntities(mapId).stream()
                .filter(e -> entityType.equals(e.getEntityType()) && entityId.equals(e.getEntityId()))
                .findFirst()
                .orElse(null);
    }
}

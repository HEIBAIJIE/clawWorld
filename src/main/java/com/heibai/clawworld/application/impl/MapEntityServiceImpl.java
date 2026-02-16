package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 地图实体管理服务实现
 */
@Service
@RequiredArgsConstructor
public class MapEntityServiceImpl implements MapEntityService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final ConfigDataManager configDataManager;
    private final PlayerSessionService playerSessionService;

    @Override
    public EntityInfo inspectCharacter(String playerId, String characterName) {
        // 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return EntityInfo.error("玩家不存在");
        }

        PlayerEntity player = playerOpt.get();

        // 查找目标角色（在同一地图上）
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
                .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(player.getCurrentMapId()))
                .collect(Collectors.toList());

        // 查找目标玩家（通过昵称匹配）
        for (PlayerEntity target : playersOnMap) {
            if (target.getName() != null && target.getName().equals(characterName)) {
                // 构建详细的角色信息
                StringBuilder info = new StringBuilder();

                info.append("=== 角色信息 ===\n");
                info.append("昵称: ").append(target.getName()).append("\n");
                info.append("等级: ").append(target.getLevel()).append("\n");
                info.append("位置: (").append(target.getX()).append(", ").append(target.getY()).append(")\n\n");

                info.append("=== 生命与法力 ===\n");
                info.append("生命值: ").append(target.getCurrentHealth()).append("/").append(target.getMaxHealth()).append("\n");
                info.append("法力值: ").append(target.getCurrentMana()).append("/").append(target.getMaxMana()).append("\n\n");

                info.append("=== 战斗属性 ===\n");
                info.append("物理攻击: ").append(target.getPhysicalAttack()).append("\n");
                info.append("物理防御: ").append(target.getPhysicalDefense()).append("\n");
                info.append("法术攻击: ").append(target.getMagicAttack()).append("\n");
                info.append("法术防御: ").append(target.getMagicDefense()).append("\n");
                info.append("速度: ").append(target.getSpeed()).append("\n");
                info.append("暴击率: ").append(String.format("%.1f%%", target.getCritRate() * 100)).append("\n");
                info.append("暴击伤害: ").append(String.format("%.1f%%", target.getCritDamage() * 100)).append("\n");

                return EntityInfo.success(characterName, "PLAYER", info.toString());
            }
        }

        // TODO: 查找敌人和NPC
        // 这里需要从EnemyInstanceRepository和NpcInstanceRepository中查找

        return EntityInfo.error("未找到目标角色: " + characterName);
    }

    @Override
    @Transactional
    public MoveResult movePlayer(String playerId, int targetX, int targetY) {
        // 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return MoveResult.error("玩家不存在");
        }

        PlayerEntity player = playerOpt.get();

        // 检查是否在战斗中
        if (player.isInCombat()) {
            return MoveResult.error("战斗中无法移动");
        }

        // 获取地图配置
        MapConfig mapConfig = configDataManager.getMap(player.getCurrentMapId());
        if (mapConfig == null) {
            return MoveResult.error("地图不存在");
        }

        // 检查目标位置是否在地图范围内
        if (targetX < 0 || targetX >= mapConfig.getWidth() || targetY < 0 || targetY >= mapConfig.getHeight()) {
            return MoveResult.error("目标位置超出地图范围");
        }

        // 简化的寻路：检查目标位置是否可达
        // 实际应该实现A*算法或其他寻路算法
        // 这里简化为直接移动
        if (!isPositionPassable(mapConfig, targetX, targetY)) {
            return MoveResult.error("目标位置不可通过");
        }

        // 计算移动距离
        int distance = Math.abs(targetX - player.getX()) + Math.abs(targetY - player.getY());

        // 根据设计文档：以每0.5秒1格的速度移动
        // 这里简化处理，直接移动到目标位置
        player.setX(targetX);
        player.setY(targetY);
        playerRepository.save(player);

        // 如果距离大于1，返回移动中状态
        if (distance > 1) {
            return MoveResult.moving(targetX, targetY);
        } else {
            return MoveResult.success(targetX, targetY, "移动完成");
        }
    }

    @Override
    @Transactional
    public InteractionResult interact(String playerId, String targetName, String option) {
        // 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return InteractionResult.error("玩家不存在");
        }

        PlayerEntity player = playerOpt.get();

        // 根据交互选项处理不同的交互
        switch (option.toLowerCase()) {
            case "attack":
                // 攻击交互：发起战斗
                return InteractionResult.successWithWindowChange(
                        "发起战斗",
                        "combat_" + UUID.randomUUID().toString(),
                        "COMBAT"
                );

            case "talk":
                // 对话交互
                return InteractionResult.success("与 " + targetName + " 对话");

            case "shop":
                // 商店交互
                return InteractionResult.successWithWindowChange(
                        "打开商店",
                        "shop_" + targetName,
                        "SHOP"
                );

            case "teleport":
                // 传送交互
                return InteractionResult.success("传送到 " + targetName);

            case "loot":
                // 拾取交互
                return InteractionResult.success("拾取 " + targetName);

            default:
                return InteractionResult.error("未知的交互选项: " + option);
        }
    }

    @Override
    public List<MapEntity> getNearbyInteractableEntities(String playerId) {
        // 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return Collections.emptyList();
        }

        PlayerEntity player = playerOpt.get();

        // 获取周围9格的实体
        List<MapEntity> nearbyEntities = new ArrayList<>();

        // 查找同一地图上的其他玩家
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
                .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(player.getCurrentMapId()))
                .filter(p -> !p.getId().equals(playerId))
                .filter(p -> isInRange(player.getX(), player.getY(), p.getX(), p.getY(), 1))
                .collect(Collectors.toList());

        // 转换为MapEntity（简化处理）
        for (PlayerEntity p : playersOnMap) {
            Player domainPlayer = playerMapper.toDomain(p);
            nearbyEntities.add(domainPlayer);
        }

        // 添加其他类型的实体（NPC、敌人、传送点等）
        // 注意：这些实体的数据应该从配置或数据库中获取
        // 当前实现为简化版本，实际使用时需要：
        // 1. 从地图配置中获取该地图的NPC、敌人、传送点等实体
        // 2. 检查这些实体是否在玩家附近（九宫格范围内）
        // 3. 将它们转换为对应的领域对象并添加到列表中

        return nearbyEntities;
    }

    @Override
    public List<MapEntity> getMapEntities(String mapId) {
        // 获取地图上的所有实体
        List<MapEntity> entities = new ArrayList<>();

        // 获取地图上的所有玩家
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
                .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(mapId))
                .collect(Collectors.toList());

        for (PlayerEntity p : playersOnMap) {
            Player domainPlayer = playerMapper.toDomain(p);
            entities.add(domainPlayer);
        }

        // 添加其他类型的实体（NPC、敌人、传送点等）
        // 注意：这些实体的数据应该从配置或数据库中获取
        // 当前实现为简化版本，实际使用时需要：
        // 1. 从地图配置中获取该地图的所有NPC、敌人、传送点等实体
        // 2. 将它们转换为对应的领域对象并添加到列表中
        // 3. 对于敌人，还需要检查是否已被击败（从战斗记录中查询）

        return entities;
    }

    /**
     * 检查位置是否可通过
     * 根据设计文档：树、岩石、山脉、河流、海洋、墙不可通过
     */
    private boolean isPositionPassable(MapConfig mapConfig, int x, int y) {
        // 检查坐标是否在地图范围内
        if (x < 0 || y < 0 || x >= mapConfig.getWidth() || y >= mapConfig.getHeight()) {
            return false;
        }

        // 获取该位置的地形配置
        List<String> terrainTypes = configDataManager.getMapTerrain(mapConfig.getId(), x, y);

        // 检查是否有不可通过的地形
        Set<String> impassableTerrains = Set.of("树", "岩石", "山脉", "河流", "海洋", "墙",
            "TREE", "ROCK", "MOUNTAIN", "RIVER", "OCEAN", "WALL");

        for (String terrain : terrainTypes) {
            if (impassableTerrains.contains(terrain)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查两个位置是否在指定范围内
     */
    private boolean isInRange(int x1, int y1, int x2, int y2, int range) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        return dx <= range && dy <= range;
    }
}

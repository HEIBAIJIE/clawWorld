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
    private final com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository enemyInstanceRepository;
    private final com.heibai.clawworld.infrastructure.persistence.repository.NpcShopInstanceRepository npcShopInstanceRepository;

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

        // 查找敌人
        List<com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity> enemiesOnMap =
            enemyInstanceRepository.findByMapId(player.getCurrentMapId());

        for (com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity enemy : enemiesOnMap) {
            if (enemy.getDisplayName() != null && enemy.getDisplayName().equals(characterName)) {
                // 获取敌人模板配置
                com.heibai.clawworld.infrastructure.config.data.character.EnemyConfig enemyConfig =
                    configDataManager.getEnemy(enemy.getTemplateId());

                if (enemyConfig == null) {
                    return EntityInfo.error("敌人配置不存在: " + enemy.getTemplateId());
                }

                // 构建敌人详细信息
                StringBuilder info = new StringBuilder();

                info.append("=== 敌人信息 ===\n");
                info.append("名称: ").append(enemy.getDisplayName()).append("\n");
                info.append("等级: ").append(enemyConfig.getLevel()).append("\n");
                info.append("品阶: ").append(enemyConfig.getTier()).append("\n");
                info.append("位置: (").append(enemy.getX()).append(", ").append(enemy.getY()).append(")\n");
                info.append("状态: ").append(enemy.isDead() ? "已死亡" : "存活").append("\n\n");

                if (!enemy.isDead()) {
                    info.append("=== 生命与法力 ===\n");
                    info.append("生命值: ").append(enemy.getCurrentHealth()).append("/").append(enemyConfig.getHealth()).append("\n");
                    info.append("法力值: ").append(enemy.getCurrentMana()).append("/").append(enemyConfig.getMana()).append("\n\n");

                    info.append("=== 战斗属性 ===\n");
                    info.append("物理攻击: ").append(enemyConfig.getPhysicalAttack()).append("\n");
                    info.append("物理防御: ").append(enemyConfig.getPhysicalDefense()).append("\n");
                    info.append("法术攻击: ").append(enemyConfig.getMagicAttack()).append("\n");
                    info.append("法术防御: ").append(enemyConfig.getMagicDefense()).append("\n");
                    info.append("速度: ").append(enemyConfig.getSpeed()).append("\n");
                    info.append("暴击率: ").append(String.format("%.1f%%", enemyConfig.getCritRate() * 100)).append("\n");
                    info.append("暴击伤害: ").append(String.format("%.1f%%", enemyConfig.getCritDamage() * 100)).append("\n");
                    info.append("命中率: ").append(String.format("%.1f%%", enemyConfig.getHitRate() * 100)).append("\n");
                    info.append("闪避率: ").append(String.format("%.1f%%", enemyConfig.getDodgeRate() * 100)).append("\n\n");

                    info.append("=== 掉落信息 ===\n");
                    info.append("经验: ").append(enemyConfig.getExpMin()).append(" - ").append(enemyConfig.getExpMax()).append("\n");
                    info.append("金钱: ").append(enemyConfig.getGoldMin()).append(" - ").append(enemyConfig.getGoldMax()).append("\n");
                    info.append("刷新时间: ").append(enemyConfig.getRespawnSeconds()).append("秒\n");
                } else {
                    long remainingTime = (enemy.getLastDeathTime() + enemyConfig.getRespawnSeconds() * 1000L - System.currentTimeMillis()) / 1000;
                    if (remainingTime > 0) {
                        info.append("刷新倒计时: ").append(remainingTime).append("秒\n");
                    } else {
                        info.append("即将刷新\n");
                    }
                }

                return EntityInfo.success(characterName, "ENEMY", info.toString());
            }
        }

        // 查找NPC
        List<com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity> npcsOnMap =
            npcShopInstanceRepository.findByMapId(player.getCurrentMapId());

        for (com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity npcShop : npcsOnMap) {
            // 获取NPC配置
            com.heibai.clawworld.infrastructure.config.data.character.NpcConfig npcConfig =
                configDataManager.getNpc(npcShop.getNpcId());

            if (npcConfig != null && npcConfig.getName().equals(characterName)) {
                // 构建NPC详细信息
                StringBuilder info = new StringBuilder();

                info.append("=== NPC信息 ===\n");
                info.append("名称: ").append(npcConfig.getName()).append("\n");
                info.append("描述: ").append(npcConfig.getDescription()).append("\n\n");

                if (npcConfig.isHasShop()) {
                    info.append("=== 商店信息 ===\n");
                    info.append("可以与此NPC进行交易\n");
                    info.append("当前金钱: ").append(npcShop.getCurrentGold()).append("\n");
                    info.append("刷新间隔: ").append(npcConfig.getShopRefreshSeconds()).append("秒\n\n");
                }

                if (npcConfig.isHasDialogue()) {
                    info.append("=== 对话 ===\n");
                    info.append("可以与此NPC对话\n");
                }

                return EntityInfo.success(characterName, "NPC", info.toString());
            }
        }

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
        // 阻塞请求，直到移动完毕
        if (distance > 0) {
            try {
                // 每格0.5秒，总共需要 distance * 500 毫秒
                Thread.sleep(distance * 500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MoveResult.error("移动被中断");
            }
        }

        // 移动到目标位置
        player.setX(targetX);
        player.setY(targetY);
        playerRepository.save(player);

        return MoveResult.success(targetX, targetY, String.format("移动完成，当前位置: (%d, %d)", targetX, targetY));
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
            case "攻击":
                // 攻击交互：发起战斗
                return InteractionResult.successWithWindowChange(
                        "发起战斗",
                        "combat_" + UUID.randomUUID().toString(),
                        "COMBAT"
                );

            case "talk":
            case "交谈":
                // 对话交互 - 查找NPC并返回对话内容
                // 注意：需要实现NPC实例查找
                // 当前简化处理，返回占位符
                // 未来需要实现：
                // 1. 从NpcInstanceRepository查找NPC
                // 2. 获取NPC的对话列表
                // 3. 返回对话内容
                return InteractionResult.success("与 " + targetName + " 对话：\n（对话内容需要从NPC配置中读取）");

            case "shop":
            case "商店":
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

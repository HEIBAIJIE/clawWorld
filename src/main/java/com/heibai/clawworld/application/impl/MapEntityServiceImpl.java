package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.*;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.NpcShopInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
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
    private final EnemyInstanceRepository enemyInstanceRepository;
    private final NpcShopInstanceRepository npcShopInstanceRepository;
    private final PartyService partyService;
    private final TradeService tradeService;
    private final CombatService combatService;
    private final CharacterInfoService characterInfoService;

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
                // 使用 CharacterInfoService 生成角色信息（不包含背包）
                Player targetPlayer = playerSessionService.getPlayerState(target.getId());
                if (targetPlayer != null) {
                    String info = characterInfoService.generateOtherPlayerInfo(targetPlayer);
                    return EntityInfo.success(characterName, "PLAYER", info);
                }
                return EntityInfo.error("无法获取玩家状态");
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
        if (distance > 1) {
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

        // 处理传送选项（格式：传送到地图名·传送点名）
        if (option.startsWith("传送到")) {
            return handleTeleport(player, targetName, option);
        }

        // 根据交互选项处理不同的交互
        switch (option.toLowerCase()) {
            case "inspect":
            case "查看":
                // 查看交互：返回角色详细信息
                EntityInfo entityInfo = inspectCharacter(playerId, targetName);
                return entityInfo.isSuccess() ?
                    InteractionResult.success(entityInfo.getAttributes().toString()) :
                    InteractionResult.error(entityInfo.getMessage());

            case "attack":
            case "攻击":
                // 攻击交互：发起战斗
                // 首先尝试查找敌人
                List<com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity> enemies =
                    enemyInstanceRepository.findByMapId(player.getCurrentMapId());
                for (var enemy : enemies) {
                    if (enemy.getDisplayName() != null && enemy.getDisplayName().equals(targetName)) {
                        // 找到敌人，发起PVE战斗
                        com.heibai.clawworld.application.service.CombatService.CombatResult combatResult =
                            combatService.initiateCombatWithEnemy(playerId, targetName, player.getCurrentMapId());
                        if (combatResult.isSuccess()) {
                            return InteractionResult.successWithWindowChange(
                                combatResult.getMessage(),
                                combatResult.getCombatId(),
                                "COMBAT"
                            );
                        } else {
                            return InteractionResult.error(combatResult.getMessage());
                        }
                    }
                }
                // 如果不是敌人，尝试查找玩家（PVP战斗）
                // 先通过玩家名称查找玩家ID
                List<PlayerEntity> playersOnMapForPvp = playerRepository.findAll().stream()
                    .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(player.getCurrentMapId()))
                    .filter(p -> p.getName() != null && p.getName().equals(targetName))
                    .collect(Collectors.toList());
                if (playersOnMapForPvp.isEmpty()) {
                    return InteractionResult.error("目标不存在: " + targetName);
                }
                String targetPlayerId = playersOnMapForPvp.get(0).getId();
                com.heibai.clawworld.application.service.CombatService.CombatResult pvpResult =
                    combatService.initiateCombat(playerId, targetPlayerId);
                if (pvpResult.isSuccess()) {
                    return InteractionResult.successWithWindowChange(
                        pvpResult.getMessage(),
                        pvpResult.getCombatId(),
                        "COMBAT"
                    );
                } else {
                    return InteractionResult.error(pvpResult.getMessage());
                }

            case "talk":
            case "交谈":
                // 对话交互 - 查找NPC并返回对话内容
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
            case "传送":
                // 旧的传送交互（不应该再被使用）
                return InteractionResult.error("请使用具体的传送选项，如：传送到城镇广场·城镇入口");

            case "loot":
            case "拾取":
                // 拾取交互
                return InteractionResult.success("拾取 " + targetName);

            case "休息":
            case "rest":
                // 篝火休息交互：回满生命和法力
                player.setCurrentHealth(player.getMaxHealth());
                player.setCurrentMana(player.getMaxMana());
                playerRepository.save(player);
                return InteractionResult.success("你在篝火旁休息，生命和法力已完全恢复");

            // 玩家间交互选项
            case "邀请组队":
            case "invite party":
                com.heibai.clawworld.application.service.PartyService.PartyResult inviteResult =
                    partyService.invitePlayer(playerId, targetName);
                return inviteResult.isSuccess() ?
                    InteractionResult.success(inviteResult.getMessage()) :
                    InteractionResult.error(inviteResult.getMessage());

            case "接受组队邀请":
            case "accept party invite":
                com.heibai.clawworld.application.service.PartyService.PartyResult acceptInviteResult =
                    partyService.acceptInvite(playerId, targetName);
                return acceptInviteResult.isSuccess() ?
                    InteractionResult.success(acceptInviteResult.getMessage()) :
                    InteractionResult.error(acceptInviteResult.getMessage());

            case "拒绝组队邀请":
            case "reject party invite":
                com.heibai.clawworld.application.service.PartyService.PartyResult rejectInviteResult =
                    partyService.rejectInvite(playerId, targetName);
                return rejectInviteResult.isSuccess() ?
                    InteractionResult.success(rejectInviteResult.getMessage()) :
                    InteractionResult.error(rejectInviteResult.getMessage());

            case "请求加入队伍":
            case "request join party":
                com.heibai.clawworld.application.service.PartyService.PartyResult requestJoinResult =
                    partyService.requestJoin(playerId, targetName);
                return requestJoinResult.isSuccess() ?
                    InteractionResult.success(requestJoinResult.getMessage()) :
                    InteractionResult.error(requestJoinResult.getMessage());

            case "接受组队请求":
            case "accept party request":
                com.heibai.clawworld.application.service.PartyService.PartyResult acceptRequestResult =
                    partyService.acceptJoinRequest(playerId, targetName);
                return acceptRequestResult.isSuccess() ?
                    InteractionResult.success(acceptRequestResult.getMessage()) :
                    InteractionResult.error(acceptRequestResult.getMessage());

            case "拒绝组队请求":
            case "reject party request":
                com.heibai.clawworld.application.service.PartyService.PartyResult rejectRequestResult =
                    partyService.rejectJoinRequest(playerId, targetName);
                return rejectRequestResult.isSuccess() ?
                    InteractionResult.success(rejectRequestResult.getMessage()) :
                    InteractionResult.error(rejectRequestResult.getMessage());

            case "请求交易":
            case "request trade":
                com.heibai.clawworld.application.service.TradeService.TradeResult tradeRequestResult =
                    tradeService.requestTrade(playerId, targetName);
                if (tradeRequestResult.isSuccess()) {
                    // 发起交易请求时不切换窗口，等待对方接受
                    if (tradeRequestResult.getWindowId() != null) {
                        return InteractionResult.successWithWindowChange(
                            tradeRequestResult.getMessage(),
                            tradeRequestResult.getWindowId(),
                            "TRADE"
                        );
                    } else {
                        return InteractionResult.success(tradeRequestResult.getMessage());
                    }
                } else {
                    return InteractionResult.error(tradeRequestResult.getMessage());
                }

            case "接受交易请求":
            case "accept trade request":
                com.heibai.clawworld.application.service.TradeService.TradeResult acceptTradeResult =
                    tradeService.acceptTradeRequest(playerId, targetName);
                if (acceptTradeResult.isSuccess()) {
                    return InteractionResult.successWithWindowChange(
                        acceptTradeResult.getMessage(),
                        acceptTradeResult.getWindowId(),
                        "TRADE"
                    );
                } else {
                    return InteractionResult.error(acceptTradeResult.getMessage());
                }

            case "拒绝交易请求":
            case "reject trade request":
                com.heibai.clawworld.application.service.TradeService.OperationResult rejectTradeResult =
                    tradeService.rejectTradeRequest(playerId, targetName);
                return rejectTradeResult.isSuccess() ?
                    InteractionResult.success(rejectTradeResult.getMessage()) :
                    InteractionResult.error(rejectTradeResult.getMessage());

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
     * 根据设计文档：
     * - 树、岩石、山脉、河流、海洋、墙不可通过
     * - 在消灭敌人以前，无法进入敌人所在的格子
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

        // 检查该位置是否有存活的敌人
        List<com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity> enemies =
            enemyInstanceRepository.findByMapId(mapConfig.getId());
        for (var enemy : enemies) {
            if (enemy.getX() == x && enemy.getY() == y && !enemy.isDead()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 处理传送交互
     * @param player 玩家实体
     * @param waypointName 传送点名称
     * @param option 传送选项（格式：传送到地图名·传送点名）
     * @return 交互结果
     */
    private InteractionResult handleTeleport(PlayerEntity player, String waypointName, String option) {
        // 解析目标：传送到地图名·传送点名
        String targetDisplayName = option.substring("传送到".length());

        // 查找当前传送点
        var currentWaypointConfig = findWaypointByName(player.getCurrentMapId(), waypointName);
        if (currentWaypointConfig == null) {
            return InteractionResult.error("找不到传送点: " + waypointName);
        }

        // 检查玩家是否在传送点附近（九宫格范围内）
        int dx = Math.abs(player.getX() - currentWaypointConfig.getX());
        int dy = Math.abs(player.getY() - currentWaypointConfig.getY());
        if (dx > 1 || dy > 1) {
            return InteractionResult.error("你不在传送点附近，请先移动到传送点");
        }

        // 查找目标传送点
        var targetWaypointConfig = findWaypointByDisplayName(targetDisplayName, currentWaypointConfig.getConnectedWaypointIds());
        if (targetWaypointConfig == null) {
            return InteractionResult.error("无法传送到: " + targetDisplayName + "，该目的地不在可传送列表中");
        }

        // 获取目标地图配置
        var targetMapConfig = configDataManager.getMap(targetWaypointConfig.getMapId());
        if (targetMapConfig == null) {
            return InteractionResult.error("目标地图不存在: " + targetWaypointConfig.getMapId());
        }

        // 执行传送：更新玩家位置和地图
        String oldMapId = player.getCurrentMapId();
        player.setCurrentMapId(targetWaypointConfig.getMapId());
        player.setX(targetWaypointConfig.getX());
        player.setY(targetWaypointConfig.getY());

        // 如果传送到安全区，恢复生命和法力
        if (targetMapConfig.isSafe()) {
            player.setCurrentHealth(player.getMaxHealth());
            player.setCurrentMana(player.getMaxMana());
        }

        playerRepository.save(player);

        // 构建传送成功消息
        String message = String.format("传送成功！从 %s 传送到 %s·%s (位置: %d, %d)",
            oldMapId, targetMapConfig.getName(), targetWaypointConfig.getName(),
            targetWaypointConfig.getX(), targetWaypointConfig.getY());

        if (targetMapConfig.isSafe()) {
            message += "\n【安全区域】生命和法力已恢复";
        }

        // 返回窗口变化，触发地图窗口刷新
        return InteractionResult.successWithWindowChange(
            message,
            "map_" + targetWaypointConfig.getMapId(),
            "MAP"
        );
    }

    /**
     * 根据名称查找地图上的传送点配置
     */
    private com.heibai.clawworld.infrastructure.config.data.map.WaypointConfig findWaypointByName(String mapId, String waypointName) {
        for (var wp : configDataManager.getAllWaypoints()) {
            if (wp.getMapId().equals(mapId) && wp.getName().equals(waypointName)) {
                return wp;
            }
        }
        return null;
    }

    /**
     * 根据显示名称（地图名·传送点名）查找传送点配置
     * @param displayName 显示名称（格式：地图名·传送点名）
     * @param connectedWaypointIds 允许的传送点ID列表
     */
    private com.heibai.clawworld.infrastructure.config.data.map.WaypointConfig findWaypointByDisplayName(
            String displayName, java.util.List<String> connectedWaypointIds) {
        if (connectedWaypointIds == null || connectedWaypointIds.isEmpty()) {
            return null;
        }

        for (String wpId : connectedWaypointIds) {
            var wp = configDataManager.getWaypoint(wpId);
            if (wp != null) {
                var mapConfig = configDataManager.getMap(wp.getMapId());
                String mapName = mapConfig != null ? mapConfig.getName() : wp.getMapId();
                String wpDisplayName = mapName + "·" + wp.getName();
                if (wpDisplayName.equals(displayName)) {
                    return wp;
                }
            }
        }
        return null;
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

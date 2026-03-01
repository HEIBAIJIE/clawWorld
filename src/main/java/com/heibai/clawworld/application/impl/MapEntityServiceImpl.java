package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.*;
import com.heibai.clawworld.infrastructure.config.data.map.ChestConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.ChestInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.ChestInstanceRepository;
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
 * 地图实体管理服务实现（门面类）
 * 将具体实现委托给各专门服务
 */
@Service
@RequiredArgsConstructor
public class MapEntityServiceImpl implements MapEntityService {

    private final PlayerRepository playerRepository;
    private final ConfigDataManager configDataManager;
    private final PlayerSessionService playerSessionService;
    private final EnemyInstanceRepository enemyInstanceRepository;
    private final NpcShopInstanceRepository npcShopInstanceRepository;
    private final ChestInstanceRepository chestInstanceRepository;

    // 委托服务
    private final PathfindingService pathfindingService;
    private final MapEntityQueryService mapEntityQueryService;
    private final TeleportService teleportService;
    private final DialogueService dialogueService;
    private final RestService restService;
    private final PartyService partyService;
    private final TradeService tradeService;
    private final CombatService combatService;
    private final CharacterInfoService characterInfoService;
    private final ChestService chestService;

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
                Player targetPlayer = playerSessionService.getPlayerState(target.getId());
                if (targetPlayer != null) {
                    String info = characterInfoService.generateOtherPlayerInfo(targetPlayer);
                    return EntityInfo.success(characterName, "PLAYER", info);
                }
                return EntityInfo.error("无法获取玩家状态");
            }
        }

        // 查找敌人
        var enemiesOnMap = enemyInstanceRepository.findByMapId(player.getCurrentMapId());
        for (var enemy : enemiesOnMap) {
            if (enemy.getDisplayName() != null && enemy.getDisplayName().equals(characterName)) {
                var enemyConfig = configDataManager.getEnemy(enemy.getTemplateId());
                if (enemyConfig == null) {
                    return EntityInfo.error("敌人配置不存在: " + enemy.getTemplateId());
                }

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
        var npcsOnMap = npcShopInstanceRepository.findByMapId(player.getCurrentMapId());
        for (var npcShop : npcsOnMap) {
            var npcConfig = configDataManager.getNpc(npcShop.getNpcId());
            if (npcConfig != null && npcConfig.getName().equals(characterName)) {
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

        // 查找宝箱
        var chestsOnMap = chestInstanceRepository.findByMapId(player.getCurrentMapId());
        for (var chest : chestsOnMap) {
            if (chest.getDisplayName() != null && chest.getDisplayName().equals(characterName)) {
                ChestConfig chestConfig = configDataManager.getChest(chest.getTemplateId());
                if (chestConfig == null) {
                    return EntityInfo.error("宝箱配置不存在: " + chest.getTemplateId());
                }

                StringBuilder info = new StringBuilder();
                info.append("=== 宝箱信息 ===\n");
                info.append("名称: ").append(chest.getDisplayName()).append("\n");
                info.append("描述: ").append(chestConfig.getDescription()).append("\n");
                info.append("位置: (").append(chest.getX()).append(", ").append(chest.getY()).append(")\n");
                info.append("类型: ").append("SMALL".equals(chest.getChestType()) ? "小宝箱（个人）" : "大宝箱（服务器）").append("\n\n");

                if ("SMALL".equals(chest.getChestType())) {
                    boolean hasOpened = chest.hasPlayerOpened(playerId);
                    info.append("状态: ").append(hasOpened ? "已开启" : "未开启").append("\n");
                    if (hasOpened) {
                        info.append("（你已经开启过此宝箱）\n");
                    }
                } else {
                    if (chest.isOpened()) {
                        long now = System.currentTimeMillis();
                        long respawnTime = chest.getLastOpenTime() + chestConfig.getRespawnSeconds() * 1000L;
                        if (now < respawnTime) {
                            int remaining = (int) ((respawnTime - now) / 1000);
                            info.append("状态: 已开启\n");
                            info.append("刷新倒计时: ").append(remaining).append("秒\n");
                        } else {
                            info.append("状态: 可开启\n");
                        }
                    } else {
                        info.append("状态: 可开启\n");
                    }
                    info.append("刷新时间: ").append(chestConfig.getRespawnSeconds()).append("秒\n");
                }

                return EntityInfo.success(characterName, "CHEST", info.toString());
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

        int startX = player.getX();
        int startY = player.getY();

        // 如果已经在目标位置
        if (startX == targetX && startY == targetY) {
            return MoveResult.success(targetX, targetY, "你已经在目标位置");
        }

        // 检查目标位置是否可通行
        if (!pathfindingService.isPositionPassable(player.getCurrentMapId(), targetX, targetY)) {
            return MoveResult.error("目标位置不可通过");
        }

        // 使用 PathfindingService 寻找最短路径
        List<int[]> path = pathfindingService.findPath(player.getCurrentMapId(), startX, startY, targetX, targetY);
        if (path == null || path.isEmpty()) {
            return MoveResult.error("无法到达目标位置");
        }

        // 逐步移动
        int stepCount = path.size();
        for (int i = 0; i < stepCount; i++) {
            int[] step = path.get(i);

            // 距离大于1时，从第二步开始每步等待0.5秒
            if (stepCount > 1 && i > 0) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    playerRepository.save(player);
                    return MoveResult.error("移动中断于(" + player.getX() + ", " + player.getY() + ")");
                }
            }

            // 更新玩家位置
            player.setX(step[0]);
            player.setY(step[1]);
        }

        // 保存最终位置
        playerRepository.save(player);

        return MoveResult.success(targetX, targetY, String.format("移动至(%d, %d)", targetX, targetY));
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
            String targetDisplayName = option.substring("传送到".length());
            TeleportService.TeleportResult result = teleportService.teleport(playerId, targetName, targetDisplayName);
            if (result.isSuccess()) {
                return InteractionResult.successWithWindowChange(
                        result.getMessage(),
                        "map_" + result.getNewMapId(),
                        "MAP"
                );
            } else {
                return InteractionResult.error(result.getMessage());
            }
        }

        // 根据交互选项处理不同的交互
        switch (option.toLowerCase()) {
            case "inspect":
            case "查看":
                EntityInfo entityInfo = inspectCharacter(playerId, targetName);
                return entityInfo.isSuccess() ?
                        InteractionResult.success(entityInfo.getAttributes().toString()) :
                        InteractionResult.error(entityInfo.getMessage());

            case "attack":
            case "攻击":
                return handleAttack(player, targetName);

            case "talk":
            case "交谈":
                DialogueService.DialogueResult dialogueResult = dialogueService.talk(playerId, targetName);
                return dialogueResult.isSuccess() ?
                        InteractionResult.success(dialogueResult.getMessage()) :
                        InteractionResult.error(dialogueResult.getMessage());

            case "shop":
            case "商店":
                return handleShop(player, targetName);

            case "teleport":
            case "传送":
                return InteractionResult.error("请使用具体的传送选项，如：传送到城镇广场·城镇入口");

            case "loot":
            case "拾取":
                return InteractionResult.success("拾取 " + targetName);

            case "休息":
            case "rest":
                RestService.RestResult restResult = restService.rest(playerId, targetName);
                return restResult.isSuccess() ?
                        InteractionResult.success(restResult.getMessage()) :
                        InteractionResult.error(restResult.getMessage());

            // 宝箱交互
            case "打开":
            case "open":
                return handleOpenChest(playerId, targetName);

            // 组队交互
            case "邀请组队":
            case "invite party":
                return handlePartyResult(partyService.invitePlayer(playerId, targetName));

            case "接受组队邀请":
            case "accept party invite":
                return handlePartyResult(partyService.acceptInvite(playerId, targetName));

            case "拒绝组队邀请":
            case "reject party invite":
                return handlePartyResult(partyService.rejectInvite(playerId, targetName));

            case "请求加入队伍":
            case "request join party":
                return handlePartyResult(partyService.requestJoin(playerId, targetName));

            case "接受组队请求":
            case "accept party request":
                return handlePartyResult(partyService.acceptJoinRequest(playerId, targetName));

            case "拒绝组队请求":
            case "reject party request":
                return handlePartyResult(partyService.rejectJoinRequest(playerId, targetName));

            // 交易交互
            case "请求交易":
            case "request trade":
                return handleTradeRequest(playerId, targetName);

            case "接受交易请求":
            case "accept trade request":
                return handleAcceptTrade(playerId, targetName);

            case "拒绝交易请求":
            case "reject trade request":
                TradeService.OperationResult rejectResult = tradeService.rejectTradeRequest(playerId, targetName);
                return rejectResult.isSuccess() ?
                        InteractionResult.success(rejectResult.getMessage()) :
                        InteractionResult.error(rejectResult.getMessage());

            default:
                return InteractionResult.error("未知的交互选项: " + option);
        }
    }

    @Override
    public List<MapEntity> getNearbyInteractableEntities(String playerId) {
        return mapEntityQueryService.getNearbyInteractableEntities(playerId);
    }

    @Override
    public List<MapEntity> getMapEntities(String mapId) {
        return mapEntityQueryService.getMapEntities(mapId);
    }

    @Override
    public List<MapEntity> getMapEntities(String mapId, String playerId) {
        return mapEntityQueryService.getMapEntities(mapId, playerId);
    }

    @Override
    public boolean isPositionPassable(String mapId, int x, int y) {
        return pathfindingService.isPositionPassable(mapId, x, y);
    }

    @Override
    public Set<String> calculateReachabilityMap(String playerId) {
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return new HashSet<>();
        }

        PlayerEntity player = playerOpt.get();
        return pathfindingService.calculateReachabilityMap(player.getCurrentMapId(), player.getX(), player.getY());
    }

    // ========== 私有辅助方法 ==========

    private InteractionResult handleAttack(PlayerEntity player, String targetName) {
        // 首先尝试查找敌人
        var enemies = enemyInstanceRepository.findByMapId(player.getCurrentMapId());
        for (var enemy : enemies) {
            if (enemy.getDisplayName() != null && enemy.getDisplayName().equals(targetName)) {
                CombatService.CombatResult combatResult =
                        combatService.initiateCombatWithEnemy(player.getId(), targetName, player.getCurrentMapId());
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
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
                .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(player.getCurrentMapId()))
                .filter(p -> p.getName() != null && p.getName().equals(targetName))
                .collect(Collectors.toList());

        if (playersOnMap.isEmpty()) {
            return InteractionResult.error("目标不存在: " + targetName);
        }

        String targetPlayerId = playersOnMap.get(0).getId();
        CombatService.CombatResult pvpResult = combatService.initiateCombat(player.getId(), targetPlayerId);
        if (pvpResult.isSuccess()) {
            return InteractionResult.successWithWindowChange(
                    pvpResult.getMessage(),
                    pvpResult.getCombatId(),
                    "COMBAT"
            );
        } else {
            return InteractionResult.error(pvpResult.getMessage());
        }
    }

    private InteractionResult handleShop(PlayerEntity player, String targetName) {
        var npcsForShop = npcShopInstanceRepository.findByMapId(player.getCurrentMapId());
        for (var npcShop : npcsForShop) {
            var npcConfig = configDataManager.getNpc(npcShop.getNpcId());
            if (npcConfig != null && npcConfig.getName().equals(targetName) && npcConfig.isHasShop()) {
                player.setCurrentShopId(npcShop.getNpcId());
                playerRepository.save(player);
                return InteractionResult.successWithWindowChange(
                        "打开商店",
                        npcShop.getNpcId(),
                        "SHOP"
                );
            }
        }
        return InteractionResult.error("找不到商店: " + targetName);
    }

    private InteractionResult handlePartyResult(PartyService.PartyResult result) {
        return result.isSuccess() ?
                InteractionResult.success(result.getMessage()) :
                InteractionResult.error(result.getMessage());
    }

    private InteractionResult handleTradeRequest(String playerId, String targetName) {
        TradeService.TradeResult result = tradeService.requestTrade(playerId, targetName);
        if (result.isSuccess()) {
            if (result.getWindowId() != null) {
                return InteractionResult.successWithWindowChange(
                        result.getMessage(),
                        result.getWindowId(),
                        "TRADE"
                );
            } else {
                return InteractionResult.success(result.getMessage());
            }
        } else {
            return InteractionResult.error(result.getMessage());
        }
    }

    private InteractionResult handleAcceptTrade(String playerId, String targetName) {
        TradeService.TradeResult result = tradeService.acceptTradeRequest(playerId, targetName);
        if (result.isSuccess()) {
            return InteractionResult.successWithWindowChange(
                    result.getMessage(),
                    result.getWindowId(),
                    "TRADE"
            );
        } else {
            return InteractionResult.error(result.getMessage());
        }
    }

    private InteractionResult handleOpenChest(String playerId, String targetName) {
        ChestService.OpenChestResult result = chestService.openChest(playerId, targetName);
        return result.isSuccess() ?
                InteractionResult.successWithInventoryChange(result.getMessage()) :
                InteractionResult.error(result.getMessage());
    }
}

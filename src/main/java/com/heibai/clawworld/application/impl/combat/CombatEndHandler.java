package com.heibai.clawworld.application.impl.combat;

import com.heibai.clawworld.application.service.WindowStateService;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.service.CombatSettlementService;
import com.heibai.clawworld.domain.window.WindowTransition;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 战斗结束处理器 - 负责处理战斗结束后的各种状态更新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CombatEndHandler {

    private final CombatSettlementService settlementService;
    private final ConfigDataManager configDataManager;
    private final PlayerRepository playerRepository;
    private final EnemyInstanceRepository enemyInstanceRepository;
    private final WindowStateService windowStateService;
    private final CombatRewardDistributor rewardDistributor;

    // 战斗结束处理完成的信号量，用于等待处理完成
    private final Map<String, CountDownLatch> combatEndLatches = new ConcurrentHashMap<>();

    /**
     * 处理战斗结束时的窗口状态转换和战利品分配
     * 将所有参战玩家的窗口状态从COMBAT转换回MAP
     */
    public void handleCombatEnd(String combatId) {
        try {
            // 获取战利品分配结果（原子操作）
            CombatInstance.RewardDistribution distribution = settlementService.getAndRemoveRewardDistribution(combatId);

            if (distribution == null) {
                // 另一个线程已经在处理战斗结束，等待处理完成
                log.debug("战斗结束处理已由其他线程执行，等待完成: combatId={}", combatId);
                waitForCombatEndProcessing(combatId);
                return;
            }

            // 创建信号量，让其他线程可以等待
            CountDownLatch latch = new CountDownLatch(1);
            combatEndLatches.put(combatId, latch);

            try {
                // 处理战利品分配
                if (distribution.isEnemiesNeedReset()) {
                    resetEnemyStates(distribution);
                } else {
                    rewardDistributor.distributeRewards(distribution);
                    updateDefeatedEnemies(distribution);
                }

                // 处理被击败玩家的传送和经验惩罚
                handleDefeatedPlayers(distribution);

                // 同步玩家的战斗后状态（生命和法力）- 只对存活玩家
                syncPlayerFinalStates(distribution);

                // 处理窗口状态转换
                handleWindowTransitions(combatId, distribution);
            } finally {
                // 通知等待的线程处理完成
                latch.countDown();
                // 延迟清理信号量，确保其他线程有机会获取
                scheduleCleanup(combatId);
            }

        } catch (Exception e) {
            log.error("处理战斗结束失败: combatId={}", combatId, e);
        }
    }

    /**
     * 等待战斗结束处理完成
     */
    private void waitForCombatEndProcessing(String combatId) {
        CountDownLatch latch = combatEndLatches.get(combatId);
        if (latch != null) {
            try {
                // 最多等待2秒
                boolean completed = latch.await(2, TimeUnit.SECONDS);
                if (!completed) {
                    log.warn("等待战斗结束处理超时: combatId={}", combatId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待战斗结束处理被中断: combatId={}", combatId);
            }
        }
    }

    /**
     * 延迟清理信号量
     */
    private void scheduleCleanup(String combatId) {
        // 使用一个简单的延迟清理，5秒后移除
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                combatEndLatches.remove(combatId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 处理窗口状态转换
     */
    private void handleWindowTransitions(String combatId, CombatInstance.RewardDistribution distribution) {
        // 收集被击败玩家的ID
        Set<String> defeatedPlayerIds = new HashSet<>();
        if (distribution.getDefeatedPlayers() != null) {
            for (CombatInstance.DefeatedPlayer dp : distribution.getDefeatedPlayers()) {
                defeatedPlayerIds.add(dp.getPlayerId());
            }
        }

        // 从数据库中查找所有combatId匹配的玩家
        List<PlayerEntity> playersInCombat = playerRepository.findAll().stream()
            .filter(p -> combatId.equals(p.getCombatId()))
            .collect(Collectors.toList());

        if (playersInCombat.isEmpty() && defeatedPlayerIds.isEmpty()) {
            log.debug("没有找到参战玩家，可能战斗状态已被清理: combatId={}", combatId);
            return;
        }

        List<WindowTransition> transitions = new ArrayList<>();

        // 处理存活的玩家（清除战斗状态）
        for (PlayerEntity player : playersInCombat) {
            String playerId = player.getId();
            String currentWindow = windowStateService.getCurrentWindowType(playerId);
            transitions.add(WindowTransition.of(playerId, currentWindow, "MAP", null));

            // 清除玩家的战斗状态
            player.setInCombat(false);
            player.setCombatId(null);
            playerRepository.save(player);
        }

        // 为被击败的玩家也添加窗口切换
        for (String defeatedPlayerId : defeatedPlayerIds) {
            String currentWindow = windowStateService.getCurrentWindowType(defeatedPlayerId);
            transitions.add(WindowTransition.of(defeatedPlayerId, currentWindow, "MAP", null));
        }

        if (!transitions.isEmpty()) {
            boolean success = windowStateService.transitionWindows(transitions);
            if (success) {
                log.info("战斗结束，所有玩家窗口状态已转换回MAP: combatId={}, playerCount={}",
                    combatId, transitions.size());
            } else {
                log.warn("战斗结束窗口状态转换失败: combatId={}", combatId);
            }
        }
    }

    /**
     * 处理被击败玩家的传送和经验惩罚
     */
    private void handleDefeatedPlayers(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getDefeatedPlayers() == null || distribution.getDefeatedPlayers().isEmpty()) {
            return;
        }

        log.info("处理被击败玩家: {} 人", distribution.getDefeatedPlayers().size());

        // 获取地图推荐等级
        Integer recommendedLevel = null;
        if (distribution.getMapId() != null) {
            MapConfig mapConfig = configDataManager.getMap(distribution.getMapId());
            if (mapConfig != null) {
                recommendedLevel = mapConfig.getRecommendedLevel();
            }
        }

        for (CombatInstance.DefeatedPlayer defeatedPlayer : distribution.getDefeatedPlayers()) {
            Optional<PlayerEntity> playerOpt = playerRepository.findById(defeatedPlayer.getPlayerId());
            if (playerOpt.isEmpty()) {
                continue;
            }

            PlayerEntity player = playerOpt.get();

            // 判断是否需要经验惩罚
            boolean shouldPenalize = shouldApplyExpPenalty(distribution, defeatedPlayer, recommendedLevel);

            // 执行经验惩罚
            if (shouldPenalize) {
                int expPenalty = (int) (player.getExperience() * 0.1);
                player.setExperience(Math.max(0, player.getExperience() - expPenalty));
                log.info("玩家 {} 被击败，扣除 10% 经验值 ({} 点)", player.getName(), expPenalty);
            }

            // 传送到上次安全传送点并恢复满状态
            teleportToSafeWaypoint(player);

            // 清除战斗状态
            player.setInCombat(false);
            player.setCombatId(null);

            playerRepository.save(player);
            log.info("玩家 {} 被击败处理完成，当前位置: ({}, {}) 地图: {}",
                player.getName(), player.getX(), player.getY(), player.getCurrentMapId());
        }
    }

    /**
     * 判断是否应该应用经验惩罚
     */
    private boolean shouldApplyExpPenalty(CombatInstance.RewardDistribution distribution,
                                           CombatInstance.DefeatedPlayer defeatedPlayer,
                                           Integer recommendedLevel) {
        if (recommendedLevel == null || defeatedPlayer.getPlayerLevel() <= recommendedLevel) {
            return false;
        }

        // 玩家等级高于地图推荐等级
        if (distribution.getCombatType() == CombatInstance.CombatType.PVP) {
            // PVP战斗：高于地图推荐等级的玩家掉落10%经验
            return true;
        } else if (distribution.getCombatType() == CombatInstance.CombatType.PVE) {
            // PVE战斗：无论是全部被击败还是部分被击败，高于推荐等级的玩家都掉落10%经验
            return true;
        }

        return false;
    }

    /**
     * 将被击败的玩家传送到上次使用的安全区域传送点，并恢复满状态
     */
    private void teleportToSafeWaypoint(PlayerEntity player) {
        String lastSafeWaypointId = player.getLastSafeWaypointId();

        if (lastSafeWaypointId != null) {
            var waypointConfig = configDataManager.getWaypoint(lastSafeWaypointId);
            if (waypointConfig != null) {
                player.setCurrentMapId(waypointConfig.getMapId());
                player.setX(waypointConfig.getX());
                player.setY(waypointConfig.getY());
                log.info("玩家 {} 被击败，传送回安全传送点: {} (地图: {}, 位置: {}, {})",
                    player.getName(), waypointConfig.getName(), waypointConfig.getMapId(),
                    waypointConfig.getX(), waypointConfig.getY());
            } else {
                teleportToDefaultSafeWaypoint(player);
            }
        } else {
            teleportToDefaultSafeWaypoint(player);
        }

        // 恢复满状态
        player.setCurrentHealth(player.getMaxHealth());
        player.setCurrentMana(player.getMaxMana());
    }

    /**
     * 传送到默认安全传送点（第一个安全地图的第一个传送点）
     */
    private void teleportToDefaultSafeWaypoint(PlayerEntity player) {
        for (MapConfig mapConfig : configDataManager.getAllMaps()) {
            if (mapConfig.isSafe()) {
                for (var waypointConfig : configDataManager.getAllWaypoints()) {
                    if (waypointConfig.getMapId().equals(mapConfig.getId())) {
                        player.setCurrentMapId(waypointConfig.getMapId());
                        player.setX(waypointConfig.getX());
                        player.setY(waypointConfig.getY());
                        player.setLastSafeWaypointId(waypointConfig.getId());
                        log.info("玩家 {} 被击败，传送回默认安全传送点: {} (地图: {})",
                            player.getName(), waypointConfig.getName(), mapConfig.getName());
                        return;
                    }
                }
            }
        }
        log.warn("找不到安全传送点，玩家 {} 保持原位置", player.getName());
    }

    /**
     * 重置敌人状态（所有玩家撤退时调用）
     */
    private void resetEnemyStates(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getEnemiesToReset() == null) {
            return;
        }

        for (CombatInstance.EnemyToReset enemyToReset : distribution.getEnemiesToReset()) {
            Optional<EnemyInstanceEntity> enemyOpt =
                enemyInstanceRepository.findByMapIdAndInstanceId(enemyToReset.getMapId(), enemyToReset.getInstanceId());

            if (enemyOpt.isPresent()) {
                var enemy = enemyOpt.get();
                enemy.setInCombat(false);
                enemy.setCombatId(null);
                enemyInstanceRepository.save(enemy);
                log.debug("敌人 {} 状态已重置（玩家撤退）", enemy.getDisplayName());
            }
        }
    }

    /**
     * 更新被击败敌人的状态
     */
    private void updateDefeatedEnemies(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getDefeatedEnemies() == null) {
            return;
        }

        for (CombatInstance.DefeatedEnemy defeatedEnemy : distribution.getDefeatedEnemies()) {
            Optional<EnemyInstanceEntity> enemyOpt =
                enemyInstanceRepository.findByMapIdAndInstanceId(defeatedEnemy.getMapId(), defeatedEnemy.getInstanceId());

            if (enemyOpt.isPresent()) {
                var enemy = enemyOpt.get();
                enemy.setDead(true);
                enemy.setLastDeathTime(System.currentTimeMillis());
                enemy.setInCombat(false);
                enemy.setCombatId(null);
                enemyInstanceRepository.save(enemy);
                log.debug("敌人 {} 被击败，将在 {} 秒后刷新",
                    enemy.getDisplayName(), defeatedEnemy.getRespawnSeconds());
            }
        }
    }

    /**
     * 同步玩家的战斗后状态（生命和法力）
     */
    private void syncPlayerFinalStates(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getPlayerFinalStates() == null) {
            return;
        }

        // 收集被击败玩家的ID
        Set<String> defeatedPlayerIds = new HashSet<>();
        if (distribution.getDefeatedPlayers() != null) {
            for (CombatInstance.DefeatedPlayer dp : distribution.getDefeatedPlayers()) {
                defeatedPlayerIds.add(dp.getPlayerId());
            }
        }

        for (Map.Entry<String, CombatInstance.PlayerFinalState> entry : distribution.getPlayerFinalStates().entrySet()) {
            String playerId = entry.getKey();

            // 跳过被击败的玩家（已在handleDefeatedPlayers中处理）
            if (defeatedPlayerIds.contains(playerId)) {
                continue;
            }

            CombatInstance.PlayerFinalState finalState = entry.getValue();

            Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
            if (playerOpt.isPresent()) {
                PlayerEntity player = playerOpt.get();
                player.setCurrentHealth(finalState.getCurrentHealth());
                player.setCurrentMana(finalState.getCurrentMana());
                playerRepository.save(player);
                log.debug("同步玩家 {} 战斗后状态: HP={}, MP={}",
                    player.getName(), finalState.getCurrentHealth(), finalState.getCurrentMana());
            }
        }
    }
}

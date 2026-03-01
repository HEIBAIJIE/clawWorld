package com.heibai.clawworld.domain.service;

import com.heibai.clawworld.domain.combat.Combat;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.domain.combat.CombatTurnWaiter;
import com.heibai.clawworld.domain.combat.TurnTimeoutManager;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战斗结算服务 - 负责战斗结束的领域逻辑
 *
 * 职责：
 * 1. 判断战斗是否结束
 * 2. 确定胜负方
 * 3. 计算战利品
 * 4. 生成战斗结束日志
 * 5. 管理战斗结束缓存
 * 6. 处理撤退/超时结束逻辑
 */
@Slf4j
@Component
public class CombatSettlementService {

    private final CombatRewardCalculator rewardCalculator;
    private final ConfigDataManager configDataManager;

    // 已结束战斗的信息缓存（供后续玩家查询）
    private final Map<String, CombatEndInfo> endedCombatCache = new ConcurrentHashMap<>();

    // 战利品分配结果缓存
    private final Map<String, CombatInstance.RewardDistribution> rewardDistributionCache = new ConcurrentHashMap<>();

    // 已结束战斗缓存的过期时间（5分钟）
    private static final long ENDED_COMBAT_CACHE_EXPIRE_MS = 5 * 60 * 1000;

    public CombatSettlementService(ConfigDataManager configDataManager) {
        this.configDataManager = configDataManager;
        this.rewardCalculator = new CombatRewardCalculator(configDataManager);
    }

    /**
     * 检查战斗是否已结束（从缓存中）
     */
    public Optional<CombatEndInfo> getEndedCombatInfo(String combatId) {
        return Optional.ofNullable(endedCombatCache.get(combatId));
    }

    /**
     * 获取并移除战利品分配结果
     * 由CombatService在战斗结束后调用，用于持久化战利品
     */
    public CombatInstance.RewardDistribution getAndRemoveRewardDistribution(String combatId) {
        return rewardDistributionCache.remove(combatId);
    }

    /**
     * 创建战斗已结束的结果
     * 用于玩家在战斗结束后发送请求时返回结束信息
     */
    public CombatEngine.CombatActionResult createCombatEndedResult(CombatEndInfo endInfo, String playerId) {
        CombatEngine.CombatActionResult result = new CombatEngine.CombatActionResult();
        result.setSuccess(true);
        result.setCombatEnded(true);
        result.setMessage("战斗已结束");
        result.setBattleLog(new ArrayList<>(endInfo.getFinalBattleLog()));

        // 标记该玩家已获取结束信息
        endInfo.markPlayerNotified(playerId);

        // 清理过期的缓存
        cleanExpiredEndedCombatCache();

        return result;
    }

    /**
     * 清理过期的战斗结束缓存
     */
    public void cleanExpiredEndedCombatCache() {
        endedCombatCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 结束战斗并处理奖励
     * @param combat 战斗实例
     * @param turnTimeoutManager 回合超时管理器
     * @param turnWaiters 回合等待器映射
     * @param activeCombatsRemover 从活跃战斗中移除的回调
     * @return 战斗结束时新增的日志列表，如果战斗未结束返回null
     */
    public List<String> finishCombat(CombatInstance combat,
                                      TurnTimeoutManager turnTimeoutManager,
                                      Map<String, CombatTurnWaiter> turnWaiters,
                                      Runnable activeCombatsRemover) {
        if (!combat.isFinished()) {
            return null;
        }

        String combatId = combat.getCombatId();

        // 使用 synchronized 确保战斗只被处理一次
        synchronized (combat) {
            if (combat.getStatus() == Combat.CombatStatus.FINISHED) {
                return null;
            }
            combat.setStatus(Combat.CombatStatus.FINISHED);
        }

        // 取消回合超时计时
        turnTimeoutManager.combatEnded(combatId);

        // 记录当前日志数量，用于获取新增的日志
        int logCountBefore = combat.getAllLogs().size();

        Optional<CombatParty> winner = combat.getWinner();
        if (winner.isPresent()) {
            combat.addLog("阵营 " + winner.get().getFactionId() + " 获得胜利！");
            handleCombatRewards(combat, winner.get());
        } else {
            combat.addLog("战斗平局！");
        }

        // 保存战利品分配结果到缓存
        if (combat.getRewardDistribution() != null) {
            rewardDistributionCache.put(combatId, combat.getRewardDistribution());
        }

        // 获取新增的日志
        List<CombatInstance.CombatLogEntry> allLogs = combat.getAllLogs();
        List<String> newLogs = new ArrayList<>();
        for (int i = logCountBefore; i < allLogs.size(); i++) {
            CombatInstance.CombatLogEntry entry = allLogs.get(i);
            newLogs.add(String.format("[#%d] %s", entry.getSequence(), entry.getMessage()));
        }

        // 缓存战斗结束信息，供后续玩家查询
        List<String> finalBattleLog = convertLogsToStrings(combat.getAllLogs());
        CombatEndInfo endInfo = new CombatEndInfo(combatId, finalBattleLog, combat.getRewardDistribution());
        endedCombatCache.put(combatId, endInfo);

        // 通知所有等待的玩家
        CombatTurnWaiter waiter = turnWaiters.get(combatId);
        if (waiter != null) {
            waiter.notifyAllWaiting();
        }

        // 从活跃战斗中移除
        activeCombatsRemover.run();
        turnWaiters.remove(combatId);

        log.info("战斗结束: combatId={}", combatId);
        return newLogs;
    }

    /**
     * 结束战斗（考虑撤退情况，仅用于PVE战斗）
     * PVE战斗结束条件：所有玩家全灭/撤退，或敌人被消灭
     * 如果所有玩家都撤退了，敌人状态应该重置
     */
    public List<String> finishCombatWithRetreat(CombatInstance combat,
                                                  TurnTimeoutManager turnTimeoutManager,
                                                  Map<String, CombatTurnWaiter> turnWaiters,
                                                  Runnable activeCombatsRemover) {
        // 检查是否所有玩家都撤退或死亡
        boolean allPlayersGone = true;
        for (CombatParty party : combat.getParties().values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.isPlayer() && character.isAlive() && !character.isRetreated()) {
                    allPlayersGone = false;
                    break;
                }
            }
            if (!allPlayersGone) break;
        }

        if (allPlayersGone) {
            // 所有玩家都撤退或死亡，敌人获胜，重置敌人状态
            combat.addLog("所有玩家已撤退，战斗结束");

            // 标记敌人需要重置状态（不是被击败）
            combat.setEnemiesNeedReset(true);

            String combatId = combat.getCombatId();
            combat.setStatus(Combat.CombatStatus.FINISHED);

            // 取消回合超时计时
            turnTimeoutManager.combatEnded(combatId);

            // 创建空的战利品分配（撤退的玩家不获得战利品）
            CombatInstance.RewardDistribution distribution = new CombatInstance.RewardDistribution();
            distribution.setEnemiesNeedReset(true);

            // 收集需要重置状态的敌人
            for (CombatParty party : combat.getParties().values()) {
                for (CombatCharacter character : party.getCharacters()) {
                    if (character.isEnemy() && character.getEnemyMapId() != null && character.getEnemyInstanceId() != null) {
                        distribution.getEnemiesToReset().add(new CombatInstance.EnemyToReset(
                            character.getEnemyMapId(), character.getEnemyInstanceId()));
                    }
                }
            }

            // 保存所有玩家的最终状态
            for (CombatParty party : combat.getParties().values()) {
                for (CombatCharacter character : party.getCharacters()) {
                    if (character.isPlayer()) {
                        distribution.getPlayerFinalStates().put(
                            character.getCharacterId(),
                            new CombatInstance.PlayerFinalState(character.getCurrentHealth(), character.getCurrentMana())
                        );
                    }
                }
            }
            combat.setRewardDistribution(distribution);
            rewardDistributionCache.put(combatId, distribution);

            // 缓存战斗结束信息
            List<String> finalBattleLog = convertLogsToStrings(combat.getAllLogs());
            CombatEndInfo endInfo = new CombatEndInfo(combatId, finalBattleLog, distribution);
            endedCombatCache.put(combatId, endInfo);

            // 通知所有等待的玩家
            CombatTurnWaiter waiter = turnWaiters.get(combatId);
            if (waiter != null) {
                waiter.notifyAllWaiting();
            }

            activeCombatsRemover.run();
            turnWaiters.remove(combatId);
            log.info("战斗结束（所有玩家撤退）: combatId={}", combatId);

            return List.of("战斗结束，所有玩家已撤退");
        }

        // 检查常规战斗结束条件
        return finishCombat(combat, turnTimeoutManager, turnWaiters, activeCombatsRemover);
    }

    /**
     * 处理战斗超时
     * 根据设计文档：
     * - 玩家vs玩家超时：不分胜负
     * - 玩家vs敌人超时：视同全部玩家死亡
     */
    public void handleCombatTimeout(CombatInstance combat,
                                     TurnTimeoutManager turnTimeoutManager,
                                     Map<String, CombatTurnWaiter> turnWaiters,
                                     Runnable activeCombatsRemover) {
        String combatId = combat.getCombatId();
        combat.setStatus(Combat.CombatStatus.TIMEOUT);
        combat.addLog("战斗超时！");

        // 取消回合超时计时
        turnTimeoutManager.combatEnded(combatId);

        if (combat.getCombatType() == CombatInstance.CombatType.PVE) {
            combat.addLog("PVE战斗超时，所有玩家视为死亡");

            for (CombatParty party : combat.getParties().values()) {
                for (CombatCharacter character : party.getCharacters()) {
                    if (character.isPlayer()) {
                        character.setDead(true);
                        character.setCurrentHealth(0);
                        combat.addLog(character.getName() + " 因超时死亡");
                    }
                }
            }
        } else if (combat.getCombatType() == CombatInstance.CombatType.PVP) {
            combat.addLog("PVP战斗超时，不分胜负");
        }

        // 缓存战斗结束信息
        List<String> finalBattleLog = convertLogsToStrings(combat.getAllLogs());
        CombatEndInfo endInfo = new CombatEndInfo(combatId, finalBattleLog, combat.getRewardDistribution());
        endedCombatCache.put(combatId, endInfo);

        // 通知所有等待的玩家
        CombatTurnWaiter waiter = turnWaiters.get(combatId);
        if (waiter != null) {
            waiter.notifyAllWaiting();
        }

        activeCombatsRemover.run();
        turnWaiters.remove(combatId);
        log.info("战斗超时: combatId={}, type={}", combatId, combat.getCombatType());
    }

    /**
     * 处理战斗奖励
     * 根据设计文档：
     * - 战利品归属于对敌人造成最后攻击的队伍
     * - 组队中战胜敌人，每个玩家都获得全部经验
     * - 金钱平分
     * - 物品归队长持有
     * - 战斗结束后玩家不会回复生命值和法力值
     * - 被击败的玩家需要传送回安全区域并可能受到经验惩罚
     */
    private void handleCombatRewards(CombatInstance combat, CombatParty winner) {
        log.info("战斗 {} 的胜利方 {} 获得战利品", combat.getCombatId(), winner.getFactionId());

        // 计算总战利品
        int totalExp = 0;
        int totalGold = 0;
        List<String> droppedItems = new ArrayList<>();

        // 遍历所有被击败的敌人，计算战利品
        for (CombatParty party : combat.getParties().values()) {
            if (party.getFactionId().equals(winner.getFactionId())) {
                continue; // 跳过胜利方
            }
            for (CombatCharacter character : party.getCharacters()) {
                if (!character.isAlive() && character.isEnemy()) {
                    // 计算这个敌人的战利品
                    CombatRewardCalculator.CombatReward reward = rewardCalculator.calculateEnemyReward(character, winner.getFactionId());
                    totalExp += reward.getExperience();
                    totalGold += reward.getGold();
                    if (reward.getItems() != null) {
                        droppedItems.addAll(reward.getItems());
                    }
                }
            }
        }

        // 获取胜利方的所有存活玩家
        List<CombatCharacter> winnerPlayers = winner.getCharacters().stream()
            .filter(c -> c.isPlayer() && c.isAlive())
            .collect(Collectors.toList());

        // 创建战利品分配结果
        CombatInstance.RewardDistribution distribution = new CombatInstance.RewardDistribution();
        distribution.setWinnerFactionId(winner.getFactionId());
        distribution.setCombatType(combat.getCombatType());
        distribution.setMapId(combat.getMapId());

        // 统计所有玩家是否都被击败（用于PVE惩罚判断）
        boolean allPlayersDefeated = true;
        for (CombatParty party : combat.getParties().values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.isPlayer() && character.isAlive() && !character.isRetreated()) {
                    allPlayersDefeated = false;
                    break;
                }
            }
            if (!allPlayersDefeated) break;
        }

        // 保存所有玩家的最终状态（包括所有参战方的玩家）
        for (CombatParty party : combat.getParties().values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.isPlayer()) {
                    distribution.getPlayerFinalStates().put(
                        character.getCharacterId(),
                        new CombatInstance.PlayerFinalState(character.getCurrentHealth(), character.getCurrentMana())
                    );
                    // 记录被击败的玩家（死亡且不是撤退的）
                    if (!character.isAlive() && !character.isRetreated()) {
                        distribution.getDefeatedPlayers().add(
                            new CombatInstance.DefeatedPlayer(
                                character.getCharacterId(),
                                character.getLevel(),
                                allPlayersDefeated
                            )
                        );
                    }
                }
                // 记录被击败的敌人
                if (character.isEnemy() && !character.isAlive()) {
                    if (character.getEnemyMapId() != null && character.getEnemyInstanceId() != null) {
                        distribution.getDefeatedEnemies().add(
                            new CombatInstance.DefeatedEnemy(
                                character.getEnemyMapId(),
                                character.getEnemyInstanceId(),
                                character.getEnemyRespawnSeconds()
                            )
                        );
                    }
                }
            }
        }

        if (winnerPlayers.isEmpty()) {
            // 没有存活的玩家，不分配战利品
            combat.addLog("=== 战利品 ===");
            combat.addLog("没有存活的玩家，战利品无法分配");
            combat.setRewardDistribution(distribution);
            return;
        }

        // 计算分配
        int playerCount = winnerPlayers.size();
        int goldPerPlayer = totalGold / playerCount;

        // 找到队长（第一个玩家视为队长，或者有partyLeader标记的）
        CombatCharacter leader = winnerPlayers.stream()
            .filter(CombatCharacter::isPartyLeader)
            .findFirst()
            .orElse(winnerPlayers.get(0));

        distribution.setTotalExperience(totalExp);
        distribution.setTotalGold(totalGold);
        distribution.setGoldPerPlayer(goldPerPlayer);
        distribution.setItems(droppedItems);
        distribution.setLeaderId(leader.getCharacterId());
        distribution.setPlayerIds(winnerPlayers.stream()
            .map(CombatCharacter::getCharacterId)
            .collect(Collectors.toList()));
        combat.setRewardDistribution(distribution);

        // 添加战利品日志
        combat.addLog("=== 战利品分配 ===");

        if (totalExp > 0) {
            if (playerCount == 1) {
                combat.addLog(leader.getName() + " 获得经验: " + totalExp);
            } else {
                combat.addLog("每人获得经验: " + totalExp + " (共" + playerCount + "人)");
            }
        }

        if (totalGold > 0) {
            if (playerCount == 1) {
                combat.addLog(leader.getName() + " 获得金钱: " + totalGold);
            } else {
                combat.addLog("金钱平分: 每人 " + goldPerPlayer + " (总计" + totalGold + ")");
            }
        }

        if (!droppedItems.isEmpty()) {
            for (String itemId : droppedItems) {
                // 尝试获取物品名称
                String itemName = itemId;
                if (configDataManager != null) {
                    var itemConfig = configDataManager.getItem(itemId);
                    if (itemConfig != null) {
                        itemName = itemConfig.getName();
                    } else {
                        var equipConfig = configDataManager.getEquipment(itemId);
                        if (equipConfig != null) {
                            // 装备显示完整格式（不含实例编号，因为还未生成）
                            String slotName = getSlotName(equipConfig.getSlot());
                            itemName = "[" + slotName + "]" + equipConfig.getName();
                        }
                    }
                }
                combat.addLog(leader.getName() + " 获得物品: " + itemName);
            }
        }

        if (totalExp == 0 && totalGold == 0 && droppedItems.isEmpty()) {
            combat.addLog("没有获得任何战利品");
        }
    }

    /**
     * 将日志条目转换为字符串列表
     */
    private List<String> convertLogsToStrings(List<CombatInstance.CombatLogEntry> logs) {
        return logs.stream()
            .map(entry -> String.format("[#%d] %s", entry.getSequence(), entry.getMessage()))
            .collect(Collectors.toList());
    }

    /**
     * 获取装备槽位的中文名称
     */
    private String getSlotName(String slot) {
        if (slot == null) return "未知";
        switch (slot.toUpperCase()) {
            case "HEAD": return "头部";
            case "CHEST": return "胸部";
            case "LEGS": return "腿部";
            case "FEET": return "脚部";
            case "RIGHT_HAND":
            case "MAIN_HAND": return "右手";
            case "LEFT_HAND":
            case "OFF_HAND": return "左手";
            case "ACCESSORY":
            case "ACCESSORY1":
            case "ACCESSORY2": return "饰品";
            default: return slot;
        }
    }

    /**
     * 战斗结束信息
     * 用于缓存已结束战斗的信息，供后续玩家查询
     */
    @Data
    public static class CombatEndInfo {
        private String combatId;
        private long endTime;
        private List<String> finalBattleLog;
        private CombatInstance.RewardDistribution rewardDistribution;
        private Set<String> notifiedPlayers;

        public CombatEndInfo(String combatId, List<String> finalBattleLog, CombatInstance.RewardDistribution rewardDistribution) {
            this.combatId = combatId;
            this.endTime = System.currentTimeMillis();
            this.finalBattleLog = finalBattleLog;
            this.rewardDistribution = rewardDistribution;
            this.notifiedPlayers = ConcurrentHashMap.newKeySet();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - endTime > ENDED_COMBAT_CACHE_EXPIRE_MS;
        }

        public boolean hasPlayerBeenNotified(String playerId) {
            return notifiedPlayers.contains(playerId);
        }

        public void markPlayerNotified(String playerId) {
            notifiedPlayers.add(playerId);
        }
    }
}

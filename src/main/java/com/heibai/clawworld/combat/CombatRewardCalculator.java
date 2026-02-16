package com.heibai.clawworld.combat;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 战利品计算器
 * 根据设计文档计算战斗结束后的战利品
 */
public class CombatRewardCalculator {

    private final Random random;

    public CombatRewardCalculator() {
        this.random = new Random();
    }

    public CombatRewardCalculator(Random random) {
        this.random = random;
    }

    /**
     * 计算击败敌人的战利品
     * 根据设计文档：
     * - 敌人被击败后，会掉落战利品，包括经验、金钱和物品
     * - 战利品归属于对敌人造成最后攻击的队伍
     */
    public CombatReward calculateEnemyReward(CombatCharacter enemy, String winnerPartyId) {
        CombatReward reward = new CombatReward();
        reward.setPartyId(winnerPartyId);

        // TODO: 从敌人配置中获取掉落信息
        // 这里使用简化的计算
        int baseExp = enemy.getMaxHealth() * 2;
        int baseGold = enemy.getMaxHealth();

        reward.setExperience(baseExp);
        reward.setGold(baseGold);
        reward.setItems(new ArrayList<>());

        return reward;
    }

    /**
     * 计算玩家被击败的惩罚
     * 根据设计文档：
     * - 如果玩家被敌人击败，高于地图推荐等级的玩家掉落5%金钱，否则无惩罚
     * - 如果玩家被玩家击败，如果败方平均等级超过地图推荐等级，败方5%的金钱会被胜利方平分
     */
    public PlayerDefeatPenalty calculatePlayerDefeatPenalty(CombatCharacter player, boolean defeatedByEnemy,
                                                            int playerLevel, int mapRecommendedLevel) {
        PlayerDefeatPenalty penalty = new PlayerDefeatPenalty();
        penalty.setPlayerId(player.getCharacterId());

        // TODO: 从玩家数据中获取金钱
        // 这里使用简化的计算
        if (playerLevel > mapRecommendedLevel) {
            penalty.setGoldLost(0); // 需要从实际玩家数据计算5%
            penalty.setHasPenalty(true);
        } else {
            penalty.setGoldLost(0);
            penalty.setHasPenalty(false);
        }

        return penalty;
    }

    /**
     * 战利品
     */
    @Data
    public static class CombatReward {
        private String partyId; // 获得战利品的队伍ID
        private int experience;
        private int gold;
        private List<String> items; // 物品ID列表
    }

    /**
     * 玩家失败惩罚
     */
    @Data
    public static class PlayerDefeatPenalty {
        private String playerId;
        private int goldLost;
        private boolean hasPenalty;
    }
}

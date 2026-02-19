package com.heibai.clawworld.domain.character;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.heibai.clawworld.domain.item.Rarity;

import java.util.Arrays;
import java.util.List;

/**
 * 敌人领域对象
 * 根据设计文档：敌人是一类特殊的角色
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Enemy extends Character {
    private EnemyTier tier;

    // 掉落
    private int expMin;
    private int expMax;
    private int goldMin;
    private int goldMax;
    private List<LootDrop> lootTable;

    // 刷新
    private int respawnSeconds;
    private Long lastDeathTime;

    // 死亡状态
    private boolean isDead;

    @Override
    public String getEntityType() {
        return "ENEMY";
    }

    @Override
    public boolean isPassable() {
        return isDead; // 敌人死亡后可通过
    }

    @Override
    public List<String> getInteractionOptions() {
        if (isDead) {
            return Arrays.asList("查看");
        }
        return Arrays.asList("查看", "攻击");
    }

    @Override
    public List<String> getInteractionOptions(String viewerFaction, boolean isMapSafe) {
        if (isDead) {
            return Arrays.asList("查看");
        }
        return Arrays.asList("查看", "攻击");
    }

    /**
     * 获取剩余刷新时间（秒）
     * @return 剩余秒数，如果未死亡或无刷新时间则返回 -1
     */
    public long getRemainingRespawnSeconds() {
        if (!isDead || lastDeathTime == null || respawnSeconds <= 0) {
            return -1;
        }
        long elapsedSeconds = (System.currentTimeMillis() - lastDeathTime) / 1000;
        long remaining = respawnSeconds - elapsedSeconds;
        return Math.max(0, remaining);
    }

    public enum EnemyTier {
        NORMAL, ELITE, MAP_BOSS, SERVER_BOSS
    }

    @Data
    public static class LootDrop {
        private String itemId;
        private Rarity rarity;
        private double dropRate;
    }
}

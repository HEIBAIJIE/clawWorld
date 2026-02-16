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

    @Override
    public String getEntityType() {
        return "ENEMY";
    }

    @Override
    public boolean isPassable() {
        return false; // 敌人未被消灭前不可通过
    }

    @Override
    public List<String> getInteractionOptions() {
        return Arrays.asList("查看", "攻击");
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

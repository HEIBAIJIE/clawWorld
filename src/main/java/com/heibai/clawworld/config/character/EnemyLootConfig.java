package com.heibai.clawworld.config.character;

import lombok.Data;

/**
 * 敌人掉落配置 - 从CSV读取
 */
@Data
public class EnemyLootConfig {
    private String enemyId;
    private String itemId;
    private String rarity;
    private double dropRate;
    private int minQuantity;
    private int maxQuantity;
}

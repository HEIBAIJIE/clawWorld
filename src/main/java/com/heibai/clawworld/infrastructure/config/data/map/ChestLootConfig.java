package com.heibai.clawworld.infrastructure.config.data.map;

import lombok.Data;

/**
 * 宝箱掉落配置 - 从CSV读取
 */
@Data
public class ChestLootConfig {
    private String chestId;
    private String itemId;
    private String rarity;
    private double dropRate;
    private int minQuantity;
    private int maxQuantity;
}

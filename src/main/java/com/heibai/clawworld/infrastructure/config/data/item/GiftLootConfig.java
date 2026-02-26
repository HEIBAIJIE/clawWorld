package com.heibai.clawworld.infrastructure.config.data.item;

import lombok.Data;

/**
 * 礼包内容配置 - 从CSV读取
 */
@Data
public class GiftLootConfig {
    private String giftId;
    private String itemId;
    private String rarity;
    private int quantity;
}

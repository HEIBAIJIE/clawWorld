package com.heibai.clawworld.config.character;

import lombok.Data;

/**
 * NPC商店物品配置 - 从CSV读取
 */
@Data
public class NpcShopItemConfig {
    private String npcId;
    private String itemId;
    private int quantity;
}

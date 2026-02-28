package com.heibai.clawworld.infrastructure.config.data.character;

import lombok.Data;

/**
 * NPC配置 - 从CSV读取的扁平化配置
 */
@Data
public class NpcConfig {
    private String id;
    private String name;
    private String description;
    private boolean hasShop;
    private boolean hasDialogue;
    private String dialogues;
    private int shopGold;
    private int shopRefreshSeconds;
    private double priceMultiplier;

    // GUI资源
    private String walkSprite;
    private String portrait;
}

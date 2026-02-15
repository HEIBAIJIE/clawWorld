package com.heibai.clawworld.domain.character;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * NPC领域对象
 * 根据设计文档：友善NPC是一类特殊的角色，他们不会主动攻击玩家，并且处于同一个阵营
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Npc extends Character {
    private boolean hasShop;
    private boolean hasDialogue;
    private List<String> dialogues;
    private int shopGold;
    private int shopRefreshSeconds;
    private double priceMultiplier;
    private Shop shop;

    @Override
    public String getEntityType() {
        return "NPC";
    }

    @Override
    public boolean isPassable() {
        return false; // NPC不可通过
    }

    @Data
    public static class Shop {
        private List<ShopItem> items;
        private int gold;
        private int refreshSeconds;
        private double priceMultiplier;
        private Long lastRefreshTime;
    }

    @Data
    public static class ShopItem {
        private String itemId;
        private int quantity;
        private int currentQuantity;
    }
}

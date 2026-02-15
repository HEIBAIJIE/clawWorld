package com.heibai.clawworld.domain.item;

import lombok.Data;

/**
 * 物品领域对象 - 运行时使用
 */
@Data
public class Item {
    private String id;
    private String name;
    private String description;
    private ItemType type;
    private int maxStack;
    private int basePrice;
    private String effect;
    private Integer effectValue;

    public enum ItemType {
        CONSUMABLE,
        MATERIAL,
        QUEST,
        SKILL_BOOK,
        OTHER
    }
}

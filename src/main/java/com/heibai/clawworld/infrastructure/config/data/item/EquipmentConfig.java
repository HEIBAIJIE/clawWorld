package com.heibai.clawworld.infrastructure.config.data.item;

import lombok.Data;

/**
 * 装备配置 - 从CSV读取的扁平化配置
 * 装备是一种特殊的物品，因此包含物品的基础属性
 */
@Data
public class EquipmentConfig {
    // 物品基础属性
    private String id;
    private String name;
    private String description;
    private int basePrice;

    // 装备特有属性
    private String slot;
    private String rarity;

    // 四维属性加成
    private int strength;
    private int agility;
    private int intelligence;
    private int vitality;

    // 直接战斗属性加成
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;

    // GUI资源
    private String icon;
}

package com.heibai.clawworld.config.item;

import lombok.Data;

/**
 * 装备配置 - 从CSV读取的扁平化配置
 */
@Data
public class EquipmentConfig {
    private String id;
    private String name;
    private String description;
    private String slot;
    private String rarity;
    private int strength;
    private int agility;
    private int intelligence;
    private int vitality;
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;
}

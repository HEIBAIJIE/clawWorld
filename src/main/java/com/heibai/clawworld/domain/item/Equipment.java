package com.heibai.clawworld.domain.item;

import lombok.Data;

/**
 * 装备领域对象 - 运行时使用
 */
@Data
public class Equipment {
    private String id;
    private String name;
    private String description;
    private EquipmentSlot slot;
    private Rarity rarity;
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

    public enum EquipmentSlot {
        HEAD, CHEST, LEGS, FEET, LEFT_HAND, RIGHT_HAND, ACCESSORY1, ACCESSORY2
    }
}

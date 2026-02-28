package com.heibai.clawworld.infrastructure.config.data.character;

import lombok.Data;

/**
 * 敌人配置 - 从CSV读取的扁平化配置
 */
@Data
public class EnemyConfig {
    private String id;
    private String name;
    private String description;
    private int level;
    private String tier;
    private int health;
    private int mana;
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;
    private String skills;
    private int expMin;
    private int expMax;
    private int goldMin;
    private int goldMax;
    private int respawnSeconds;

    // GUI资源
    private String walkSprite;
    private String portrait;
}

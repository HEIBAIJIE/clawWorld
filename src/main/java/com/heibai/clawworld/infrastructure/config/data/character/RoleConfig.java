package com.heibai.clawworld.infrastructure.config.data.character;

import lombok.Data;

/**
 * 职业配置 - 从CSV读取的扁平化配置
 * 职业通过原始数值来体现差异，而非四维属性
 */
@Data
public class RoleConfig {
    private String id;
    private String name;
    private String description;

    // 基础原始数值
    private int baseHealth;
    private int baseMana;
    private int basePhysicalAttack;
    private int basePhysicalDefense;
    private int baseMagicAttack;
    private int baseMagicDefense;
    private int baseSpeed;
    private double baseCritRate;
    private double baseCritDamage;
    private double baseHitRate;
    private double baseDodgeRate;

    // 每级增长的原始数值
    private double healthPerLevel;
    private double manaPerLevel;
    private double physicalAttackPerLevel;
    private double physicalDefensePerLevel;
    private double magicAttackPerLevel;
    private double magicDefensePerLevel;
    private double speedPerLevel;
    private double critRatePerLevel;
    private double critDamagePerLevel;
    private double hitRatePerLevel;
    private double dodgeRatePerLevel;

    // GUI资源
    private String walkSprite;
    private String portrait;
}

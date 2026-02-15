package com.heibai.clawworld.config.character;

import lombok.Data;

/**
 * 职业配置 - 从CSV读取的扁平化配置
 */
@Data
public class RoleConfig {
    private String id;
    private String name;
    private String description;
    private int baseHealth;
    private int baseMana;
    private int baseStrength;
    private int baseAgility;
    private int baseIntelligence;
    private int baseVitality;
    private double healthPerLevel;
    private double manaPerLevel;
    private double strengthPerLevel;
    private double agilityPerLevel;
    private double intelligencePerLevel;
    private double vitalityPerLevel;
}

package com.heibai.clawworld.domain.skill;

import lombok.Data;

/**
 * 技能领域对象 - 运行时使用
 */
@Data
public class Skill {
    private String id;
    private String name;
    private String description;
    private SkillTarget targetType;
    private DamageType damageType;
    private int manaCost;
    private int cooldown;
    private double damageMultiplier;
    private int currentCooldown;

    public enum SkillTarget {
        SELF, ALLY_SINGLE, ALLY_ALL, ENEMY_SINGLE, ENEMY_ALL
    }

    public enum DamageType {
        PHYSICAL, MAGICAL, NONE
    }
}

package com.heibai.clawworld.infrastructure.config.data.skill;

import lombok.Data;

/**
 * 技能配置 - 从CSV读取的扁平化配置
 */
@Data
public class SkillConfig {
    private String id;
    private String name;
    private String description;
    private String targetType;
    private String damageType;
    private int manaCost;
    private int cooldown;
    private double damageMultiplier;

    // GUI资源
    private String vfx;
}

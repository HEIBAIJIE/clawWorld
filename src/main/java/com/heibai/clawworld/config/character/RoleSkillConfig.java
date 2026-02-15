package com.heibai.clawworld.config.character;

import lombok.Data;

/**
 * 职业技能学习配置 - 从CSV读取
 */
@Data
public class RoleSkillConfig {
    private String roleId;
    private String skillId;
    private int learnLevel;
}

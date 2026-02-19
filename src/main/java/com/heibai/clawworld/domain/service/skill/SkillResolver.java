package com.heibai.clawworld.domain.service.skill;

import com.heibai.clawworld.domain.skill.Skill;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.skill.SkillConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 技能解析器 - 负责从配置中解析技能信息
 */
@Component
@RequiredArgsConstructor
public class SkillResolver {

    private final ConfigDataManager configDataManager;

    /**
     * 根据技能ID获取技能信息
     * 从配置管理器中获取技能配置并转换为领域对象
     */
    public Skill getSkillById(String skillId) {
        // 处理普通攻击
        if ("普通攻击".equals(skillId) || "basic_attack".equals(skillId)) {
            return createBasicAttack();
        }

        // 从配置中获取技能
        SkillConfig config = configDataManager.getSkill(skillId);
        if (config == null) {
            return null;
        }

        return convertToSkill(config);
    }

    /**
     * 根据技能名称查找技能ID
     */
    public String findSkillIdByName(String skillName) {
        // 处理特殊情况：普通攻击
        if ("普通攻击".equals(skillName) || "basic_attack".equals(skillName)) {
            return "basic_attack";
        }

        // 尝试直接作为ID使用
        if (configDataManager.getSkill(skillName) != null) {
            return skillName;
        }

        // 从所有技能中查找匹配的名称
        return configDataManager.getAllSkills().stream()
            .filter(skill -> skill.getName().equals(skillName))
            .map(SkillConfig::getId)
            .findFirst()
            .orElse("basic_attack"); // 找不到时默认使用普通攻击
    }

    /**
     * 创建普通攻击技能
     */
    private Skill createBasicAttack() {
        Skill skill = new Skill();
        skill.setId("basic_attack");
        skill.setName("普通攻击");
        skill.setTargetType(Skill.SkillTarget.ENEMY_SINGLE);
        skill.setDamageType(Skill.DamageType.PHYSICAL);
        skill.setDamageMultiplier(1.0);
        skill.setManaCost(0);
        skill.setCooldown(0);
        return skill;
    }

    /**
     * 将配置转换为领域对象
     */
    private Skill convertToSkill(SkillConfig config) {
        Skill skill = new Skill();
        skill.setId(config.getId());
        skill.setName(config.getName());
        skill.setDescription(config.getDescription());
        skill.setTargetType(Skill.SkillTarget.valueOf(config.getTargetType()));
        skill.setDamageType(Skill.DamageType.valueOf(config.getDamageType()));
        skill.setManaCost(config.getManaCost());
        skill.setCooldown(config.getCooldown());
        skill.setDamageMultiplier(config.getDamageMultiplier());
        return skill;
    }
}

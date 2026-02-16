package com.heibai.clawworld.domain.character;

import com.heibai.clawworld.domain.skill.Skill;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.heibai.clawworld.domain.map.MapEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 角色领域对象 - 所有具有战斗属性的实体的基类
 * 根据设计文档：角色是一类特殊的地图实体，他们的特殊之处在于有战斗属性
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class Character extends MapEntity {
    // 基础属性
    private int level;
    private int experience;
    private String faction; // 阵营

    // 职业基础属性（来自职业配置，用于重新计算最终属性）
    private int baseMaxHealth;
    private int baseMaxMana;
    private int basePhysicalAttack;
    private int basePhysicalDefense;
    private int baseMagicAttack;
    private int baseMagicDefense;
    private int baseSpeed;
    private double baseCritRate;
    private double baseCritDamage;
    private double baseHitRate;
    private double baseDodgeRate;

    // 生命和法力（最终值）
    private int maxHealth;
    private int currentHealth;
    private int maxMana;
    private int currentMana;

    // 战斗属性（最终值）
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;

    // 技能（存储技能ID列表）
    private List<String> skills;

    // 战斗状态
    private boolean inCombat;
    private String combatId;
    private Long combatStartTime;

    @Override
    public boolean isInteractable() {
        return true; // 角色都可以交互（查看、攻击等）
    }

    @Override
    public List<String> getInteractionOptions() {
        List<String> options = new ArrayList<>();
        options.add("查看");
        // 根据设计文档：满足条件时（阵营不同、战斗地图），可以对某一个角色发起一场战斗
        // 这里简化处理，总是显示攻击选项，具体条件在执行时检查
        options.add("攻击");
        return options;
    }
}

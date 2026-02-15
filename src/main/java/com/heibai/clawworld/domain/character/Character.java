package com.heibai.clawworld.domain.character;

import com.heibai.clawworld.domain.skill.Skill;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.heibai.clawworld.domain.map.MapEntity;

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

    // 生命和法力
    private int maxHealth;
    private int currentHealth;
    private int maxMana;
    private int currentMana;

    // 战斗属性
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;

    // 技能
    private List<Skill> skills;

    // 战斗状态
    private boolean inCombat;
    private Long combatStartTime;

    @Override
    public boolean isInteractable() {
        return true; // 角色都可以交互（查看、攻击等）
    }
}

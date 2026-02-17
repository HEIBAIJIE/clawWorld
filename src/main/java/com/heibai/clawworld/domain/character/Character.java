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

    // ==================== 升级经验计算 ====================
    // 升级所需经验公式: exp = A * level^2 + B * level + C
    // 1级升2级需要100经验，之后逐级递增
    private static final int EXP_COEFFICIENT_A = 50;   // 二次项系数
    private static final int EXP_COEFFICIENT_B = 50;   // 一次项系数
    private static final int EXP_COEFFICIENT_C = 0;    // 常数项

    /**
     * 计算升到下一级所需的总经验值
     * 公式: exp = A * level^2 + B * level + C
     * 例如: 1级升2级需要 50*1 + 50*1 + 0 = 100 经验
     *       2级升3级需要 50*4 + 50*2 + 0 = 300 经验
     *       3级升4级需要 50*9 + 50*3 + 0 = 600 经验
     */
    public int getExperienceForNextLevel() {
        return EXP_COEFFICIENT_A * level * level + EXP_COEFFICIENT_B * level + EXP_COEFFICIENT_C;
    }

    /**
     * 获取当前等级的经验进度百分比
     * @return 0-100之间的整数
     */
    public int getExperienceProgressPercent() {
        int required = getExperienceForNextLevel();
        if (required <= 0) return 100;
        return Math.min(100, (int) ((long) experience * 100 / required));
    }

    /**
     * 检查是否可以升级
     */
    public boolean canLevelUp() {
        return experience >= getExperienceForNextLevel();
    }

    /**
     * 静态方法：计算指定等级升级所需的经验值
     * 用于在没有Character实例时计算（如PlayerEntity）
     */
    public static int calculateExperienceForLevel(int level) {
        return EXP_COEFFICIENT_A * level * level + EXP_COEFFICIENT_B * level + EXP_COEFFICIENT_C;
    }

    @Override
    public boolean isInteractable() {
        return true; // 角色都可以交互（查看、攻击等）
    }

    @Override
    public List<String> getInteractionOptions() {
        List<String> options = new ArrayList<>();
        options.add("查看");
        options.add("攻击"); // 默认显示，具体条件在执行时检查
        return options;
    }

    @Override
    public List<String> getInteractionOptions(String viewerFaction, boolean isMapSafe) {
        List<String> options = new ArrayList<>();
        options.add("查看");

        // 根据设计文档：满足条件时（阵营不同、战斗地图），可以对某一个角色发起一场战斗
        if (!isMapSafe && viewerFaction != null && !viewerFaction.equals(this.faction)) {
            options.add("攻击");
        }

        return options;
    }
}

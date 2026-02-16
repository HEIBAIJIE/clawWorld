package com.heibai.clawworld.combat;

import lombok.Data;

import java.util.Random;

/**
 * 伤害计算器
 * 根据设计文档实现伤害计算公式
 */
public class CombatDamageCalculator {

    private final Random random;

    public CombatDamageCalculator() {
        this.random = new Random();
    }

    public CombatDamageCalculator(Random random) {
        this.random = random;
    }

    /**
     * 计算伤害
     * 根据设计文档：
     * - 未暴击时：对应攻击力 - 对应防御力
     * - 暴击时：(对应攻击力 - 对应防御力) × (150% + 暴击伤害)
     * - 当攻击力小于防御力时，视为比防御力高1点
     * - 命中率公式：命中率 - 闪避率
     */
    public DamageResult calculateDamage(CombatCharacter attacker, CombatCharacter target, boolean isPhysical, double damageMultiplier) {
        DamageResult result = new DamageResult();

        // 1. 判断是否命中
        boolean hit = checkHit(attacker.getHitRate(), target.getDodgeRate());
        result.setHit(hit);

        if (!hit) {
            result.setDamage(0);
            result.setMissed(true);
            return result;
        }

        // 2. 判断是否暴击
        boolean crit = checkCrit(attacker.getCritRate());
        result.setCrit(crit);

        // 3. 计算基础伤害
        int attack = isPhysical ? attacker.getPhysicalAttack() : attacker.getMagicAttack();
        int defense = isPhysical ? target.getPhysicalDefense() : target.getMagicDefense();

        int baseDamage = attack - defense;
        if (baseDamage <= 0) {
            baseDamage = 1;
        }

        // 4. 应用技能倍率
        baseDamage = (int) (baseDamage * damageMultiplier);

        // 5. 应用暴击
        int finalDamage;
        if (crit) {
            finalDamage = (int) (baseDamage * (1.5 + attacker.getCritDamage()));
        } else {
            finalDamage = baseDamage;
        }

        result.setDamage(finalDamage);
        return result;
    }

    /**
     * 判断是否命中
     * 根据设计文档：命中率 - 闪避率
     */
    public boolean checkHit(double hitRate, double dodgeRate) {
        double finalHitRate = hitRate - dodgeRate;
        return random.nextDouble() < finalHitRate;
    }

    /**
     * 判断是否暴击
     */
    public boolean checkCrit(double critRate) {
        return random.nextDouble() < critRate;
    }

    /**
     * 伤害结果
     */
    @Data
    public static class DamageResult {
        private int damage;
        private boolean hit;
        private boolean missed;
        private boolean crit;
    }
}

package com.heibai.clawworld.domain.service.skill;

import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.service.CombatDamageCalculator;
import com.heibai.clawworld.domain.skill.Skill;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能执行器 - 负责执行技能效果
 */
@Component
@RequiredArgsConstructor
public class SkillExecutor {

    private final CombatDamageCalculator damageCalculator;

    /**
     * 执行技能
     * @return 执行结果，包含日志信息
     */
    public SkillExecutionResult executeSkill(CombatInstance combat, CombatCharacter caster,
                                              Skill skill, String targetId) {
        SkillExecutionResult result = new SkillExecutionResult();
        result.setSuccess(true);

        switch (skill.getTargetType()) {
            case SELF -> executeSelfSkill(combat, caster, skill, result);
            case ALLY_SINGLE -> executeAllySingleSkill(combat, caster, targetId, skill, result);
            case ALLY_ALL -> executeAllyAllSkill(combat, caster, skill, result);
            case ENEMY_SINGLE -> executeEnemySingleSkill(combat, caster, targetId, skill, result);
            case ENEMY_ALL -> executeEnemyAllSkill(combat, caster, skill, result);
        }

        return result;
    }

    /**
     * 执行对自身的技能
     */
    private void executeSelfSkill(CombatInstance combat, CombatCharacter caster,
                                   Skill skill, SkillExecutionResult result) {
        result.addLog(caster.getName() + " 对自己使用了 " + skill.getName());

        if (skill.getDamageType() == Skill.DamageType.NONE) {
            // 治疗或增益技能
            int healAmount = (int) (caster.getMagicAttack() * skill.getDamageMultiplier());
            caster.heal(healAmount);
            result.addLog(caster.getName() + " 恢复了 " + healAmount + " 点生命值");
        }
    }

    /**
     * 执行对友方单体的技能
     */
    private void executeAllySingleSkill(CombatInstance combat, CombatCharacter caster,
                                         String targetId, Skill skill, SkillExecutionResult result) {
        CombatCharacter target = combat.findCharacter(targetId);
        if (target == null || !target.isAlive()) {
            result.addLog("目标不存在或已死亡");
            return;
        }

        result.addLog(caster.getName() + " 对 " + target.getName() + " 使用了 " + skill.getName());

        if (skill.getDamageType() == Skill.DamageType.NONE) {
            // 治疗技能
            int healAmount = (int) (caster.getMagicAttack() * skill.getDamageMultiplier());
            target.heal(healAmount);
            result.addLog(target.getName() + " 恢复了 " + healAmount + " 点生命值");
        }
    }

    /**
     * 执行对友方群体的技能
     */
    private void executeAllyAllSkill(CombatInstance combat, CombatCharacter caster,
                                      Skill skill, SkillExecutionResult result) {
        List<CombatCharacter> allies = combat.getAliveCharactersInFaction(caster.getFactionId());

        result.addLog(caster.getName() + " 使用了 " + skill.getName());

        for (CombatCharacter ally : allies) {
            if (skill.getDamageType() == Skill.DamageType.NONE) {
                int healAmount = (int) (caster.getMagicAttack() * skill.getDamageMultiplier());
                ally.heal(healAmount);
                result.addLog(ally.getName() + " 恢复了 " + healAmount + " 点生命值");
            }
        }
    }

    /**
     * 执行对敌方单体的技能
     */
    private void executeEnemySingleSkill(CombatInstance combat, CombatCharacter caster,
                                          String targetId, Skill skill, SkillExecutionResult result) {
        CombatCharacter target = combat.findCharacter(targetId);
        if (target == null || !target.isAlive()) {
            result.addLog("目标不存在或已死亡");
            return;
        }

        result.addLog(caster.getName() + " 对 " + target.getName() + " 使用了 " + skill.getName());

        boolean isPhysical = skill.getDamageType() == Skill.DamageType.PHYSICAL;
        CombatDamageCalculator.DamageResult damageResult = damageCalculator.calculateDamage(
            caster, target, isPhysical, skill.getDamageMultiplier()
        );

        if (damageResult.isMissed()) {
            result.addLog("攻击未命中！");
        } else {
            target.takeDamage(damageResult.getDamage());
            combat.recordDamage(caster.getFactionId(), target.getCharacterId(), damageResult.getDamage());

            String damageLog = String.format("造成了 %d 点伤害", damageResult.getDamage());
            if (damageResult.isCrit()) {
                damageLog += "（暴击！）";
            }
            result.addLog(damageLog);

            if (!target.isAlive()) {
                result.addLog(target.getName() + " 被击败了！");
            }
        }
    }

    /**
     * 执行对敌方群体的技能
     */
    private void executeEnemyAllSkill(CombatInstance combat, CombatCharacter caster,
                                       Skill skill, SkillExecutionResult result) {
        List<CombatCharacter> enemies = combat.getEnemyCharacters(caster.getFactionId());

        result.addLog(caster.getName() + " 使用了 " + skill.getName());

        boolean isPhysical = skill.getDamageType() == Skill.DamageType.PHYSICAL;

        for (CombatCharacter enemy : enemies) {
            CombatDamageCalculator.DamageResult damageResult = damageCalculator.calculateDamage(
                caster, enemy, isPhysical, skill.getDamageMultiplier()
            );

            if (damageResult.isMissed()) {
                result.addLog("对 " + enemy.getName() + " 的攻击未命中！");
            } else {
                enemy.takeDamage(damageResult.getDamage());
                combat.recordDamage(caster.getFactionId(), enemy.getCharacterId(), damageResult.getDamage());

                String damageLog = String.format("对 %s 造成了 %d 点伤害", enemy.getName(), damageResult.getDamage());
                if (damageResult.isCrit()) {
                    damageLog += "（暴击！）";
                }
                result.addLog(damageLog);

                if (!enemy.isAlive()) {
                    result.addLog(enemy.getName() + " 被击败了！");
                }
            }
        }
    }

    /**
     * 技能执行结果
     */
    @Data
    public static class SkillExecutionResult {
        private boolean success;
        private List<String> logs = new ArrayList<>();

        public void addLog(String log) {
            logs.add(log);
        }
    }
}

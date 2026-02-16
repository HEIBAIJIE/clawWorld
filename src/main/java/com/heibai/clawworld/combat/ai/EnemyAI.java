package com.heibai.clawworld.combat.ai;

import com.heibai.clawworld.combat.CombatCharacter;
import com.heibai.clawworld.combat.CombatInstance;

/**
 * 敌人AI接口
 * 负责敌人的自动决策
 */
public interface EnemyAI {

    /**
     * 决策下一步行动
     *
     * @param combat 战斗实例
     * @param enemy 敌人角色
     * @return AI决策结果
     */
    AIDecision makeDecision(CombatInstance combat, CombatCharacter enemy);

    /**
     * AI决策结果
     */
    class AIDecision {
        private final DecisionType type;
        private final String skillId;
        private final String targetId;

        private AIDecision(DecisionType type, String skillId, String targetId) {
            this.type = type;
            this.skillId = skillId;
            this.targetId = targetId;
        }

        public static AIDecision attack(String skillId, String targetId) {
            return new AIDecision(DecisionType.ATTACK, skillId, targetId);
        }

        public static AIDecision skip() {
            return new AIDecision(DecisionType.SKIP, null, null);
        }

        public DecisionType getType() {
            return type;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getTargetId() {
            return targetId;
        }
    }

    enum DecisionType {
        ATTACK,  // 攻击
        SKIP     // 跳过
    }
}

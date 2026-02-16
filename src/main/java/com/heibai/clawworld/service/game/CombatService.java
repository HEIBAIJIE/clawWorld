package com.heibai.clawworld.service.game;

import com.heibai.clawworld.domain.combat.Combat;

/**
 * 战斗管理服务
 * 负责战斗的创建、执行、结算等
 */
public interface CombatService {

    /**
     * 发起战斗
     * @param attackerId 攻击者ID
     * @param targetId 目标ID
     * @return 战斗结果
     */
    CombatResult initiateCombat(String attackerId, String targetId);

    /**
     * 释放技能（非指向）
     * @param combatId 战斗ID
     * @param casterId 施法者ID
     * @param skillName 技能名称
     * @return 操作结果
     */
    ActionResult castSkill(String combatId, String casterId, String skillName);

    /**
     * 释放技能（指向）
     * @param combatId 战斗ID
     * @param casterId 施法者ID
     * @param skillName 技能名称
     * @param targetName 目标名称
     * @return 操作结果
     */
    ActionResult castSkillOnTarget(String combatId, String casterId, String skillName, String targetName);

    /**
     * 在战斗中使用物品
     * @param combatId 战斗ID
     * @param playerId 玩家ID
     * @param itemName 物品名称
     * @return 操作结果
     */
    ActionResult useItem(String combatId, String playerId, String itemName);

    /**
     * 等待（跳过回合）
     * @param combatId 战斗ID
     * @param playerId 玩家ID
     * @return 操作结果
     */
    ActionResult waitTurn(String combatId, String playerId);

    /**
     * 退出战斗（角色视为死亡）
     * @param combatId 战斗ID
     * @param playerId 玩家ID
     * @return 操作结果
     */
    ActionResult forfeit(String combatId, String playerId);

    /**
     * 获取战斗状态
     * @param combatId 战斗ID
     * @return 战斗对象
     */
    Combat getCombatState(String combatId);

    /**
     * 检查是否轮到玩家回合
     * @param combatId 战斗ID
     * @param playerId 玩家ID
     * @return 是否轮到该玩家
     */
    boolean isPlayerTurn(String combatId, String playerId);

    /**
     * 战斗结果
     */
    class CombatResult {
        private boolean success;
        private String message;
        private String combatId;
        private String windowId;

        public static CombatResult success(String combatId, String windowId, String message) {
            CombatResult result = new CombatResult();
            result.success = true;
            result.combatId = combatId;
            result.windowId = windowId;
            result.message = message;
            return result;
        }

        public static CombatResult error(String message) {
            CombatResult result = new CombatResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getCombatId() {
            return combatId;
        }

        public String getWindowId() {
            return windowId;
        }
    }

    /**
     * 战斗行动结果
     */
    class ActionResult {
        private boolean success;
        private String message;
        private boolean combatEnded;
        private String battleLog;

        public static ActionResult success(String message, String battleLog) {
            ActionResult result = new ActionResult();
            result.success = true;
            result.message = message;
            result.battleLog = battleLog;
            return result;
        }

        public static ActionResult combatEnded(String message, String battleLog) {
            ActionResult result = new ActionResult();
            result.success = true;
            result.message = message;
            result.battleLog = battleLog;
            result.combatEnded = true;
            return result;
        }

        public static ActionResult error(String message) {
            ActionResult result = new ActionResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCombatEnded() {
            return combatEnded;
        }

        public String getBattleLog() {
            return battleLog;
        }
    }
}

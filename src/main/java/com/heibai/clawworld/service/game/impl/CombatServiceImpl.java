package com.heibai.clawworld.service.game.impl;

import com.heibai.clawworld.combat.CombatCharacter;
import com.heibai.clawworld.combat.CombatEngine;
import com.heibai.clawworld.combat.CombatInstance;
import com.heibai.clawworld.domain.character.Character;
import com.heibai.clawworld.domain.combat.Combat;
import com.heibai.clawworld.service.game.CombatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 战斗服务实现 - 交互层
 * 负责处理用户请求，调用战斗引擎执行实际战斗逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CombatServiceImpl implements CombatService {

    private final CombatEngine combatEngine;

    @Override
    public CombatResult initiateCombat(String attackerId, String targetId) {
        try {
            // TODO: 从数据库获取攻击者和目标的信息
            // TODO: 检查是否满足战斗条件（阵营不同、战斗地图等）
            // TODO: 收集同格子同阵营的所有角色

            String windowId = UUID.randomUUID().toString();
            String combatId = combatEngine.createCombat("map-id"); // TODO: 获取实际地图ID

            log.info("发起战斗: combatId={}, attackerId={}, targetId={}", combatId, attackerId, targetId);

            return CombatResult.success(combatId, windowId, "战斗开始");
        } catch (Exception e) {
            log.error("发起战斗失败", e);
            return CombatResult.error("发起战斗失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult castSkill(String combatId, String playerId, String skillName) {
        try {
            // TODO: 根据技能名称获取技能ID
            String skillId = "普通攻击"; // 临时实现

            CombatEngine.CombatActionResult result = combatEngine.executeSkillWithWait(combatId, playerId, skillId, null);

            if (!result.isSuccess()) {
                return ActionResult.error(result.getMessage());
            }

            String battleLog = String.join("\n", result.getBattleLog());

            if (result.isCombatEnded()) {
                return ActionResult.combatEnded("战斗结束", battleLog);
            }

            return ActionResult.success(result.getMessage(), battleLog);
        } catch (Exception e) {
            log.error("释放技能失败", e);
            return ActionResult.error("释放技能失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult castSkillOnTarget(String combatId, String playerId, String skillName, String targetName) {
        try {
            // TODO: 根据技能名称获取技能ID
            String skillId = "普通攻击"; // 临时实现

            // 根据目标名称查找目标ID
            Optional<CombatInstance> combatOpt = combatEngine.getCombat(combatId);
            if (combatOpt.isEmpty()) {
                return ActionResult.error("战斗不存在");
            }

            CombatCharacter target = combatOpt.get().findCharacterByName(targetName);
            if (target == null) {
                return ActionResult.error("目标不存在");
            }

            CombatEngine.CombatActionResult result = combatEngine.executeSkillWithWait(combatId, playerId, skillId, target.getCharacterId());

            if (!result.isSuccess()) {
                return ActionResult.error(result.getMessage());
            }

            String battleLog = String.join("\n", result.getBattleLog());

            if (result.isCombatEnded()) {
                return ActionResult.combatEnded("战斗结束", battleLog);
            }

            return ActionResult.success(result.getMessage(), battleLog);
        } catch (Exception e) {
            log.error("释放技能失败", e);
            return ActionResult.error("释放技能失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult useItem(String combatId, String playerId, String itemName) {
        // TODO: 实现使用物品逻辑
        return ActionResult.success("使用物品: " + itemName, "");
    }

    @Override
    public ActionResult waitTurn(String combatId, String playerId) {
        try {
            CombatEngine.CombatActionResult result = combatEngine.skipTurnWithWait(combatId, playerId);

            if (!result.isSuccess()) {
                return ActionResult.error(result.getMessage());
            }

            String battleLog = String.join("\n", result.getBattleLog());

            if (result.isCombatEnded()) {
                return ActionResult.combatEnded("战斗结束", battleLog);
            }

            return ActionResult.success(result.getMessage(), battleLog);
        } catch (Exception e) {
            log.error("跳过回合失败", e);
            return ActionResult.error("跳过回合失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult forfeit(String combatId, String playerId) {
        try {
            CombatEngine.CombatActionResult result = combatEngine.forfeit(combatId, playerId);

            if (!result.isSuccess()) {
                return ActionResult.error(result.getMessage());
            }

            if (result.isCombatEnded()) {
                return ActionResult.combatEnded("战斗结束", "");
            }

            return ActionResult.success("逃离战斗", "");
        } catch (Exception e) {
            log.error("逃离战斗失败", e);
            return ActionResult.error("逃离战斗失败: " + e.getMessage());
        }
    }

    @Override
    public Combat getCombatState(String combatId) {
        Optional<CombatInstance> combatOpt = combatEngine.getCombat(combatId);
        if (combatOpt.isEmpty()) {
            return null;
        }

        // TODO: 将CombatInstance转换为Combat领域对象
        CombatInstance instance = combatOpt.get();
        Combat combat = new Combat();
        combat.setId(instance.getCombatId());
        combat.setMapId(instance.getMapId());
        combat.setStartTime(instance.getStartTime());
        combat.setStatus(instance.getStatus());

        // 转换日志格式
        List<String> logs = instance.getAllLogs().stream()
            .map(entry -> String.format("[#%d] %s", entry.getSequence(), entry.getMessage()))
            .collect(Collectors.toList());
        combat.setCombatLog(logs);

        return combat;
    }

    @Override
    public boolean isPlayerTurn(String combatId, String playerId) {
        Optional<CombatInstance> combatOpt = combatEngine.getCombat(combatId);
        if (combatOpt.isEmpty()) {
            return false;
        }

        Optional<String> currentTurn = combatOpt.get().getCurrentTurnCharacterId();
        return currentTurn.isPresent() && currentTurn.get().equals(playerId);
    }

    /**
     * 将领域角色转换为战斗角色
     */
    private CombatCharacter convertToCombatCharacter(Character character) {
        CombatCharacter combatChar = new CombatCharacter();
        combatChar.setCharacterId(character.getId());
        combatChar.setCharacterType(character.getEntityType());
        combatChar.setName(character.getName());
        combatChar.setMaxHealth(character.getMaxHealth());
        combatChar.setCurrentHealth(character.getCurrentHealth());
        combatChar.setMaxMana(character.getMaxMana());
        combatChar.setCurrentMana(character.getCurrentMana());
        combatChar.setPhysicalAttack(character.getPhysicalAttack());
        combatChar.setPhysicalDefense(character.getPhysicalDefense());
        combatChar.setMagicAttack(character.getMagicAttack());
        combatChar.setMagicDefense(character.getMagicDefense());
        combatChar.setSpeed(character.getSpeed());
        combatChar.setCritRate(character.getCritRate());
        combatChar.setCritDamage(character.getCritDamage());
        combatChar.setHitRate(character.getHitRate());
        combatChar.setDodgeRate(character.getDodgeRate());
        combatChar.setSkillIds(character.getSkills() != null ? new ArrayList<>(character.getSkills()) : new ArrayList<>());
        return combatChar;
    }
}

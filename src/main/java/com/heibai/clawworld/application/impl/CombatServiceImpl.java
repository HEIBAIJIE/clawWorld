package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.WindowStateService;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.service.CombatEngine;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.domain.character.Character;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.Combat;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.application.service.CombatService;
import com.heibai.clawworld.application.service.PlayerSessionService;
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
    private final ConfigDataManager configDataManager;
    private final PlayerSessionService playerSessionService;
    private final PlayerRepository playerRepository;
    private final WindowStateService windowStateService;
    private final AccountRepository accountRepository;

    @Override
    public CombatResult initiateCombat(String attackerId, String targetId) {
        try {
            // 从数据库获取攻击者信息
            Player attacker = playerSessionService.getPlayerState(attackerId);
            if (attacker == null) {
                return CombatResult.error("攻击者不存在");
            }

            // 从数据库获取目标信息（这里假设目标也是玩家，实际可能是敌人）
            Player target = playerSessionService.getPlayerState(targetId);
            if (target == null) {
                return CombatResult.error("目标不存在");
            }

            // 检查是否满足战斗条件
            // 1. 检查双方是否在同一地图
            if (!attacker.getMapId().equals(target.getMapId())) {
                return CombatResult.error("目标不在同一地图");
            }

            // 2. 检查地图是否为战斗地图
            MapConfig mapConfig = configDataManager.getMap(attacker.getMapId());
            if (mapConfig == null) {
                return CombatResult.error("地图不存在");
            }

            if (mapConfig.isSafe()) {
                return CombatResult.error("当前地图不允许战斗");
            }

            // 3. 检查双方阵营是否不同
            if (attacker.getFaction().equals(target.getFaction())) {
                return CombatResult.error("不能攻击同阵营角色");
            }

            // 收集同格子同阵营的所有角色
            List<Player> attackerParty = collectPartyMembers(attacker);
            List<Player> targetParty = collectPartyMembers(target);

            // 创建战斗
            String combatId = combatEngine.createCombat(attacker.getMapId());

            // 添加攻击方到战斗
            List<CombatCharacter> attackerCombatChars = attackerParty.stream()
                .map(this::convertToCombatCharacter)
                .collect(Collectors.toList());
            combatEngine.addPartyToCombat(combatId, attacker.getFaction(), attackerCombatChars);

            // 添加防守方到战斗
            List<CombatCharacter> targetCombatChars = targetParty.stream()
                .map(this::convertToCombatCharacter)
                .collect(Collectors.toList());
            combatEngine.addPartyToCombat(combatId, target.getFaction(), targetCombatChars);

            String windowId = "combat_window_" + combatId;

            log.info("发起战斗: combatId={}, attackerId={}, targetId={}, mapId={}",
                combatId, attackerId, targetId, attacker.getMapId());

            return CombatResult.success(combatId, windowId, "战斗开始");
        } catch (Exception e) {
            log.error("发起战斗失败", e);
            return CombatResult.error("发起战斗失败: " + e.getMessage());
        }
    }

    /**
     * 收集同格子同阵营的队伍成员
     */
    private List<Player> collectPartyMembers(Player player) {
        List<Player> partyMembers = new ArrayList<>();
        partyMembers.add(player);

        // 查找同一地图、同一格子、同一阵营的其他玩家
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
            .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(player.getMapId()))
            .filter(p -> p.getX() == player.getX() && p.getY() == player.getY())
            .filter(p -> !p.getId().equals(player.getId()))
            .collect(Collectors.toList());

        for (PlayerEntity entity : playersOnMap) {
            Player otherPlayer = playerSessionService.getPlayerState(entity.getId());
            if (otherPlayer != null && otherPlayer.getFaction().equals(player.getFaction())) {
                partyMembers.add(otherPlayer);
            }
        }

        return partyMembers;
    }

    @Override
    public ActionResult castSkill(String combatId, String playerId, String skillName) {
        try {
            // 根据技能名称获取技能ID
            // 如果skillName就是技能ID，直接使用；否则需要从配置中查找
            String skillId = findSkillIdByName(skillName);

            CombatEngine.CombatActionResult result = combatEngine.executeSkillWithWait(combatId, playerId, skillId, null);

            if (!result.isSuccess()) {
                return ActionResult.error(result.getMessage());
            }

            String battleLog = String.join("\n", result.getBattleLog());

            if (result.isCombatEnded()) {
                handleCombatEndWindowTransition(combatId);
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
            // 根据技能名称获取技能ID
            String skillId = findSkillIdByName(skillName);

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
                handleCombatEndWindowTransition(combatId);
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
        // 实现使用物品逻辑
        // 根据设计文档：战斗中可以使用物品（如生命药剂、法力药剂）
        // 需要：
        // 1. 从��家背包中查找物品
        // 2. 检查物品是否可在战斗中使用
        // 3. 应用物品效果（恢复生命/法力等）
        // 4. 减少物品数量
        // 注意：物品的持久化在战斗结算后进行
        log.warn("战斗中使用物品功能尚未完全实现: {}", itemName);
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
                handleCombatEndWindowTransition(combatId);
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
                handleCombatEndWindowTransition(combatId);
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

        // 将CombatInstance转换为Combat领域对象
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
        combatChar.setFactionId(character.getFaction());
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

    /**
     * 根据技能名称查找技能ID
     * 如果名称本身就是ID，直接返回；否则从配置中查找
     */
    private String findSkillIdByName(String skillName) {
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
            .map(skill -> skill.getId())
            .findFirst()
            .orElse("basic_attack"); // 找不到时默认使用普通攻击
    }

    /**
     * 处理战斗结束时的窗口状态转换
     * 将所有参战玩家的窗口状态从COMBAT转换回MAP
     */
    private void handleCombatEndWindowTransition(String combatId) {
        try {
            Optional<CombatInstance> combatOpt = combatEngine.getCombat(combatId);
            if (combatOpt.isEmpty()) {
                log.warn("战斗不存在，无法处理窗口转换: combatId={}", combatId);
                return;
            }

            CombatInstance combat = combatOpt.get();
            List<com.heibai.clawworld.domain.window.WindowTransition> transitions = new java.util.ArrayList<>();

            // 遍历所有参战方，收集所有玩家
            for (com.heibai.clawworld.domain.combat.CombatParty party : combat.getParties().values()) {
                for (CombatCharacter character : party.getCharacters()) {
                    if (character.isPlayer()) {
                        String playerId = character.getCharacterId();
                        String currentWindow = windowStateService.getCurrentWindowType(playerId);
                        transitions.add(com.heibai.clawworld.domain.window.WindowTransition.of(
                            playerId, currentWindow, "MAP", null));
                    }
                }
            }

            if (!transitions.isEmpty()) {
                boolean success = windowStateService.transitionWindows(transitions);
                if (success) {
                    log.info("战斗结束，所有玩家窗口状态已转换回MAP: combatId={}, playerCount={}",
                        combatId, transitions.size());
                } else {
                    log.warn("战斗结束窗口状态转换失败: combatId={}", combatId);
                }
            }
        } catch (Exception e) {
            log.error("处理战斗结束窗口转换失败: combatId={}", combatId, e);
        }
    }
}

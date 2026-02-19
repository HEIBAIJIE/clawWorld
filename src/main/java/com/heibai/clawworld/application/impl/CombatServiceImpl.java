package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.impl.combat.CombatEndHandler;
import com.heibai.clawworld.application.impl.combat.CombatInitiationService;
import com.heibai.clawworld.application.impl.combat.CombatProtectionChecker;
import com.heibai.clawworld.application.service.WindowStateService;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.domain.service.CombatEngine;
import com.heibai.clawworld.domain.service.skill.SkillResolver;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.Combat;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.application.service.CombatService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.infrastructure.persistence.mapper.CombatMapper;
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
    private final CombatMapper combatMapper;
    private final PlayerSessionService playerSessionService;
    private final PlayerRepository playerRepository;
    private final WindowStateService windowStateService;
    private final EnemyInstanceRepository enemyInstanceRepository;

    // 新增的服务
    private final CombatProtectionChecker protectionChecker;
    private final CombatInitiationService initiationService;
    private final CombatEndHandler endHandler;
    private final SkillResolver skillResolver;

    @Override
    public CombatResult initiateCombat(String attackerId, String targetId) {
        try {
            // 从数据库获取攻击者信息
            Player attacker = playerSessionService.getPlayerState(attackerId);
            if (attacker == null) {
                return CombatResult.error("攻击者不存在");
            }

            // 从数据库获取目标信息
            Player target = playerSessionService.getPlayerState(targetId);
            if (target == null) {
                return CombatResult.error("目标不存在");
            }

            // 检查是否在同一地图
            if (!initiationService.arePlayersOnSameMap(attacker, target)) {
                return CombatResult.error("目标不在同一地图");
            }

            // 检查地图是否允许战斗
            var mapCheck = protectionChecker.checkMapAllowsCombat(attacker.getMapId());
            if (!mapCheck.allowed()) {
                return CombatResult.error(mapCheck.errorMessage());
            }

            // 检查阵营
            var factionCheck = protectionChecker.checkFactionCanAttack(attacker.getFaction(), target.getFaction());
            if (!factionCheck.allowed()) {
                return CombatResult.error(factionCheck.errorMessage());
            }

            // 收集目标队伍成员
            List<Player> targetParty = initiationService.collectPartyMembers(target);

            // 检查PVP等级保护
            var pvpCheck = protectionChecker.checkPvpLevelProtection(attacker.getMapId(), targetParty);
            if (!pvpCheck.allowed()) {
                return CombatResult.error(pvpCheck.errorMessage());
            }

            // 检查目标是否正在战斗中且受保护
            Optional<PlayerEntity> targetEntityOpt = playerRepository.findById(targetId);
            if (targetEntityOpt.isPresent() && targetEntityOpt.get().isInCombat()) {
                var inCombatCheck = protectionChecker.checkInCombatProtection(attacker.getMapId(), targetParty);
                if (!inCombatCheck.allowed()) {
                    return CombatResult.error(inCombatCheck.errorMessage());
                }
            }

            // 收集攻击方队伍成员
            List<Player> attackerParty = initiationService.collectPartyMembers(attacker);

            // 创建战斗
            String combatId = combatEngine.createCombat(attacker.getMapId());

            // 添加攻击方到战斗
            List<CombatCharacter> attackerCombatChars = attackerParty.stream()
                .map(combatMapper::toCombatCharacter)
                .collect(Collectors.toList());
            combatEngine.addPartyToCombat(combatId, attacker.getFaction(), attackerCombatChars);

            // 添加防守方到战斗
            List<CombatCharacter> targetCombatChars = targetParty.stream()
                .map(combatMapper::toCombatCharacter)
                .collect(Collectors.toList());
            combatEngine.addPartyToCombat(combatId, target.getFaction(), targetCombatChars);

            // 更新所有参战玩家的战斗状态并切换窗口
            updatePlayersForCombat(combatId, attackerParty, targetParty);

            // 初始化第一个回合
            combatEngine.initializeFirstTurn(combatId);

            log.info("发起战斗: combatId={}, attackerId={}, targetId={}, mapId={}, attackerPartySize={}, targetPartySize={}",
                combatId, attackerId, targetId, attacker.getMapId(), attackerParty.size(), targetParty.size());

            return CombatResult.success(combatId, combatId, "战斗开始");
        } catch (Exception e) {
            log.error("发起战斗失败", e);
            return CombatResult.error("发起战斗失败: " + e.getMessage());
        }
    }

    /**
     * 更新参战玩家的战斗状态和窗口
     */
    private void updatePlayersForCombat(String combatId, List<Player>... playerGroups) {
        List<com.heibai.clawworld.domain.window.WindowTransition> transitions = new ArrayList<>();

        for (List<Player> group : playerGroups) {
            for (Player player : group) {
                Optional<PlayerEntity> playerEntityOpt = playerRepository.findById(player.getId());
                if (playerEntityOpt.isPresent()) {
                    PlayerEntity playerEntity = playerEntityOpt.get();
                    playerEntity.setInCombat(true);
                    playerEntity.setCombatId(combatId);
                    playerRepository.save(playerEntity);

                    String currentWindow = windowStateService.getCurrentWindowType(player.getId());
                    transitions.add(com.heibai.clawworld.domain.window.WindowTransition.of(
                        player.getId(), currentWindow, "COMBAT", combatId));
                }
            }
        }

        if (!transitions.isEmpty()) {
            boolean success = windowStateService.transitionWindows(transitions);
            if (!success) {
                log.warn("部分玩家窗口状态切换失败: combatId={}", combatId);
            }
        }
    }

    @Override
    public CombatResult initiateCombatWithEnemy(String attackerId, String enemyDisplayName, String mapId) {
        try {
            // 从数据库获取攻击者信息
            Player attacker = playerSessionService.getPlayerState(attackerId);
            if (attacker == null) {
                return CombatResult.error("攻击者不存在");
            }

            // 检查地图是否允许战斗
            var mapCheck = protectionChecker.checkMapAllowsCombat(mapId);
            if (!mapCheck.allowed()) {
                return CombatResult.error(mapCheck.errorMessage());
            }

            // 查找敌人实例
            List<com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity> enemiesOnMap =
                enemyInstanceRepository.findByMapId(mapId);

            com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity targetEnemy = null;
            for (var enemy : enemiesOnMap) {
                if (enemy.getDisplayName() != null && enemy.getDisplayName().equals(enemyDisplayName)) {
                    targetEnemy = enemy;
                    break;
                }
            }

            if (targetEnemy == null) {
                return CombatResult.error("目标敌人不存在: " + enemyDisplayName);
            }

            if (targetEnemy.isDead()) {
                return CombatResult.error("目标敌人已死亡，等待刷新");
            }

            if (targetEnemy.isInCombat()) {
                // 如果敌人已在战斗中，检查是否可以加入
                String existingCombatId = targetEnemy.getCombatId();
                Optional<CombatInstance> existingCombat = combatEngine.getCombat(existingCombatId);
                if (existingCombat.isPresent()) {
                    CombatInstance combat = existingCombat.get();

                    // 检查抢怪保护
                    var stealCheck = protectionChecker.checkMonsterStealProtection(mapId, combat);
                    if (!stealCheck.allowed()) {
                        return CombatResult.error(stealCheck.errorMessage());
                    }

                    // 将玩家加入现有战斗
                    List<Player> attackerParty = initiationService.collectPartyMembers(attacker);
                    List<CombatCharacter> attackerCombatChars = attackerParty.stream()
                        .map(combatMapper::toCombatCharacter)
                        .collect(Collectors.toList());
                    combatEngine.addPartyToCombat(existingCombatId, attacker.getFaction(), attackerCombatChars);

                    // 更新所有参战玩家的战斗状态并切换窗口
                    updatePlayersForCombat(existingCombatId, attackerParty);

                    log.info("玩家加入现有战斗: combatId={}, attackerId={}, enemyName={}, partySize={}",
                        existingCombatId, attackerId, enemyDisplayName, attackerParty.size());

                    return CombatResult.success(existingCombatId, existingCombatId, "加入战斗");
                }
            }

            // 检查玩家是否在敌人附近（九宫格范围内）
            if (!initiationService.isInInteractionRange(attacker, targetEnemy.getX(), targetEnemy.getY())) {
                return CombatResult.error("目标敌人不在交互范围内，请先移动到敌人附近");
            }

            // 获取敌人配置
            com.heibai.clawworld.infrastructure.config.data.character.EnemyConfig enemyConfig =
                configDataManager.getEnemy(targetEnemy.getTemplateId());
            if (enemyConfig == null) {
                return CombatResult.error("敌人配置不存在: " + targetEnemy.getTemplateId());
            }

            // 收集攻击方队伍成员
            List<Player> attackerParty = initiationService.collectPartyMembers(attacker);

            // 收集同格子的所有敌人（同一阵营）
            String enemyFaction = "enemy_" + targetEnemy.getTemplateId();
            final int targetX = targetEnemy.getX();
            final int targetY = targetEnemy.getY();
            List<com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity> enemiesAtPosition =
                enemiesOnMap.stream()
                    .filter(e -> e.getX() == targetX && e.getY() == targetY)
                    .filter(e -> !e.isDead())
                    .collect(Collectors.toList());

            // 创建战斗
            String combatId = combatEngine.createCombat(mapId);

            // 添加攻击方（玩家）到战斗
            List<CombatCharacter> attackerCombatChars = attackerParty.stream()
                .map(combatMapper::toCombatCharacter)
                .collect(Collectors.toList());
            combatEngine.addPartyToCombat(combatId, attacker.getFaction(), attackerCombatChars);

            // 添加防守方（敌人）到战斗
            List<CombatCharacter> enemyCombatChars = new ArrayList<>();
            for (var enemy : enemiesAtPosition) {
                var config = configDataManager.getEnemy(enemy.getTemplateId());
                if (config != null) {
                    CombatCharacter combatChar = combatMapper.toCombatCharacter(enemy, config);
                    enemyCombatChars.add(combatChar);

                    // 更新敌人状态为战斗中
                    enemy.setInCombat(true);
                    enemy.setCombatId(combatId);
                    enemyInstanceRepository.save(enemy);
                }
            }
            combatEngine.addPartyToCombat(combatId, enemyFaction, enemyCombatChars);

            // 更新所有参战玩家的战斗状态并切换窗口
            updatePlayersForCombat(combatId, attackerParty);

            // 初始化第一个回合
            combatEngine.initializeFirstTurn(combatId);

            log.info("发起PVE战斗: combatId={}, attackerId={}, enemyName={}, mapId={}, enemyCount={}, partySize={}",
                combatId, attackerId, enemyDisplayName, mapId, enemyCombatChars.size(), attackerParty.size());

            return CombatResult.success(combatId, combatId, "战斗开始");
        } catch (Exception e) {
            log.error("发起PVE战斗失败", e);
            return CombatResult.error("发起战斗失败: " + e.getMessage());
        }
    }

    @Override
    public ActionResult castSkill(String combatId, String playerId, String skillName) {
        try {
            // 根据技能名称获取技能ID
            String skillId = skillResolver.findSkillIdByName(skillName);

            // 获取战斗实例
            Optional<CombatInstance> combatOpt = combatEngine.getCombat(combatId);
            if (combatOpt.isEmpty()) {
                return ActionResult.error("战斗不存在");
            }
            CombatInstance combat = combatOpt.get();

            // 获取技能配置，判断是否需要目标
            String targetId = null;
            var skillConfig = configDataManager.getSkill(skillId);

            // 如果是单体攻击技能（普通攻击或其他单体技能），自动选择一个敌方目标
            boolean needsTarget = false;
            if (skillConfig != null) {
                String targetType = skillConfig.getTargetType();
                needsTarget = "ENEMY_SINGLE".equals(targetType) || "ALLY_SINGLE".equals(targetType);
            } else if ("basic_attack".equals(skillId) || "普通攻击".equals(skillId) || "普通攻击".equals(skillName)) {
                // 普通攻击默认需要目标
                needsTarget = true;
            }

            if (needsTarget) {
                // 获取玩家角色
                CombatCharacter caster = combat.findCharacter(playerId);
                if (caster == null) {
                    return ActionResult.error("施法者不存在");
                }

                // 自动选择一个敌方目标（选择第一个存活的敌人）
                List<CombatCharacter> enemies = combat.getEnemyCharacters(caster.getFactionId());
                if (enemies.isEmpty()) {
                    return ActionResult.error("没有可攻击的目标");
                }
                targetId = enemies.get(0).getCharacterId();
            }

            CombatEngine.CombatActionResult result = combatEngine.executeSkillWithWait(combatId, playerId, skillId, targetId);

            if (!result.isSuccess()) {
                return ActionResult.error(result.getMessage());
            }

            String battleLog = String.join("\n", result.getBattleLog());

            if (result.isCombatEnded()) {
                endHandler.handleCombatEnd(combatId);
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
            String skillId = skillResolver.findSkillIdByName(skillName);

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
                endHandler.handleCombatEnd(combatId);
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
        try {
            Optional<CombatInstance> combatOpt = combatEngine.getCombat(combatId);
            if (combatOpt.isEmpty()) {
                return ActionResult.error("战斗不存在");
            }
            CombatInstance combat = combatOpt.get();

            // 检查是否轮到该玩家（不推进行动条）
            Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
            if (currentTurn.isEmpty() || !currentTurn.get().equals(playerId)) {
                return ActionResult.error("还未轮到你的回合");
            }

            CombatCharacter combatChar = combat.findCharacter(playerId);
            if (combatChar == null || !combatChar.isAlive()) {
                return ActionResult.error("角色不存在或已死亡");
            }

            Player player = playerSessionService.getPlayerState(playerId);
            if (player == null) {
                return ActionResult.error("玩家不存在");
            }

            Player.InventorySlot targetSlot = null;
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem() && slot.getItem().getName().equals(itemName)) {
                    targetSlot = slot;
                    break;
                }
            }

            if (targetSlot == null) {
                return ActionResult.error("物品不存在: " + itemName);
            }

            com.heibai.clawworld.domain.item.Item item = targetSlot.getItem();
            if (item.getEffect() == null) {
                return ActionResult.error("该物品无法使用");
            }

            if (item.getType() != com.heibai.clawworld.domain.item.Item.ItemType.CONSUMABLE) {
                return ActionResult.error("只有消耗品可以在战斗中使用");
            }

            String resultMessage;
            switch (item.getEffect()) {
                case "HEAL_HP":
                    int hpRestore = item.getEffectValue() != null ? item.getEffectValue() : 0;
                    int newHp = Math.min(combatChar.getCurrentHealth() + hpRestore, combatChar.getMaxHealth());
                    int actualHp = newHp - combatChar.getCurrentHealth();
                    combatChar.setCurrentHealth(newHp);
                    resultMessage = String.format("%s 使用了 %s，恢复了 %d 点生命值 (当前: %d/%d)",
                        combatChar.getName(), itemName, actualHp, newHp, combatChar.getMaxHealth());
                    break;
                case "HEAL_MP":
                    int mpRestore = item.getEffectValue() != null ? item.getEffectValue() : 0;
                    int newMp = Math.min(combatChar.getCurrentMana() + mpRestore, combatChar.getMaxMana());
                    int actualMp = newMp - combatChar.getCurrentMana();
                    combatChar.setCurrentMana(newMp);
                    resultMessage = String.format("%s 使用了 %s，恢复了 %d 点法力值 (当前: %d/%d)",
                        combatChar.getName(), itemName, actualMp, newMp, combatChar.getMaxMana());
                    break;
                default:
                    return ActionResult.error("该物品无法在战斗中使用");
            }

            combat.addLog(resultMessage);

            targetSlot.setQuantity(targetSlot.getQuantity() - 1);
            if (targetSlot.getQuantity() <= 0) {
                player.getInventory().remove(targetSlot);
            }
            playerSessionService.savePlayerState(player);

            combat.resetActionBar(playerId);

            if (combat.isFinished()) {
                endHandler.handleCombatEnd(combatId);
                return ActionResult.combatEnded("战斗结束", resultMessage);
            }

            // 使用物品后，调用 CombatEngine 处理后续回合
            CombatEngine.CombatActionResult engineResult = combatEngine.processAfterAction(combatId, playerId);

            if (engineResult.isCombatEnded()) {
                endHandler.handleCombatEnd(combatId);
                String battleLog = resultMessage + "\n" + String.join("\n", engineResult.getBattleLog());
                return ActionResult.combatEnded("战斗结束", battleLog);
            }

            String battleLog = resultMessage + "\n" + String.join("\n", engineResult.getBattleLog());
            return ActionResult.success(engineResult.getMessage(), battleLog);
        } catch (Exception e) {
            log.error("战斗中使用物品失败", e);
            return ActionResult.error("使用物品失败: " + e.getMessage());
        }
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
                endHandler.handleCombatEnd(combatId);
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
                endHandler.handleCombatEnd(combatId);
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

        // 转换参战方信息
        List<Combat.CombatParty> parties = new ArrayList<>();
        for (Map.Entry<String, CombatParty> entry : instance.getParties().entrySet()) {
            Combat.CombatParty party = new Combat.CombatParty();
            party.setFaction(entry.getKey());

            List<Combat.CombatCharacter> characters = new ArrayList<>();
            for (CombatCharacter cc : entry.getValue().getCharacters()) {
                Combat.CombatCharacter character = new Combat.CombatCharacter();
                character.setCharacterId(cc.getCharacterId());
                character.setCharacterType(cc.getCharacterType());
                character.setName(cc.getName());
                character.setCurrentHealth(cc.getCurrentHealth());
                character.setMaxHealth(cc.getMaxHealth());
                character.setCurrentMana(cc.getCurrentMana());
                character.setMaxMana(cc.getMaxMana());
                character.setSpeed(cc.getSpeed());
                character.setDead(!cc.isAlive());

                // 转换技能冷却
                List<Combat.SkillCooldown> cooldowns = new ArrayList<>();
                if (cc.getSkillCooldowns() != null) {
                    for (Map.Entry<String, Integer> cdEntry : cc.getSkillCooldowns().entrySet()) {
                        Combat.SkillCooldown cooldown = new Combat.SkillCooldown();
                        cooldown.setSkillId(cdEntry.getKey());
                        cooldown.setRemainingTurns(cdEntry.getValue());
                        cooldowns.add(cooldown);
                    }
                }
                character.setSkillCooldowns(cooldowns);

                characters.add(character);
            }
            party.setCharacters(characters);
            parties.add(party);
        }
        combat.setParties(parties);

        // 转换行动条信息（按进度排序）
        List<Combat.ActionBarEntry> actionBarEntries = new ArrayList<>();
        List<CombatInstance.ActionBarEntry> sortedEntries = instance.getActionBar().values().stream()
            .sorted((a, b) -> Integer.compare(b.getProgress(), a.getProgress()))
            .collect(Collectors.toList());

        for (CombatInstance.ActionBarEntry abEntry : sortedEntries) {
            Combat.ActionBarEntry entry = new Combat.ActionBarEntry();
            entry.setCharacterId(abEntry.getCharacterId());
            entry.setProgress(abEntry.getProgress());
            actionBarEntries.add(entry);
        }
        combat.setActionBar(actionBarEntries);

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

    @Override
    public long getTurnStartTime(String combatId) {
        return combatEngine.getTurnStartTime(combatId);
    }

    /**
     * 根据技能名称查找技能ID
     * 如果名称本身就是ID，直接返回；否则从配置中查找
     * @deprecated 使用 SkillResolver.findSkillIdByName 代替
     */
    @Deprecated
    private String findSkillIdByName(String skillName) {
        return skillResolver.findSkillIdByName(skillName);
    }
}

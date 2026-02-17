package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.WindowStateService;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.domain.service.CombatEngine;
import com.heibai.clawworld.domain.service.PlayerLevelService;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.Combat;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.application.service.CombatService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.infrastructure.persistence.mapper.CombatMapper;
import com.heibai.clawworld.infrastructure.persistence.mapper.ConfigMapper;
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
    private final ConfigMapper configMapper;
    private final CombatMapper combatMapper;
    private final PlayerSessionService playerSessionService;
    private final PlayerRepository playerRepository;
    private final PlayerLevelService playerLevelService;
    private final WindowStateService windowStateService;
    private final AccountRepository accountRepository;
    private final EnemyInstanceRepository enemyInstanceRepository;

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
                .map(combatMapper::toCombatCharacter)
                .collect(Collectors.toList());
            combatEngine.addPartyToCombat(combatId, attacker.getFaction(), attackerCombatChars);

            // 添加防守方到战斗
            List<CombatCharacter> targetCombatChars = targetParty.stream()
                .map(combatMapper::toCombatCharacter)
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
    public CombatResult initiateCombatWithEnemy(String attackerId, String enemyDisplayName, String mapId) {
        try {
            // 从数据库获取攻击者信息
            Player attacker = playerSessionService.getPlayerState(attackerId);
            if (attacker == null) {
                return CombatResult.error("攻击者不存在");
            }

            // 检查地图是否为战斗地图
            MapConfig mapConfig = configDataManager.getMap(mapId);
            if (mapConfig == null) {
                return CombatResult.error("地图不存在");
            }

            if (mapConfig.isSafe()) {
                return CombatResult.error("当前地图不允许战斗");
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
                // 如果敌人已在战斗中，加入现有战斗
                String existingCombatId = targetEnemy.getCombatId();
                Optional<CombatInstance> existingCombat = combatEngine.getCombat(existingCombatId);
                if (existingCombat.isPresent()) {
                    // 将玩家加入现有战斗
                    List<Player> attackerParty = collectPartyMembers(attacker);
                    List<CombatCharacter> attackerCombatChars = attackerParty.stream()
                        .map(combatMapper::toCombatCharacter)
                        .collect(Collectors.toList());
                    combatEngine.addPartyToCombat(existingCombatId, attacker.getFaction(), attackerCombatChars);

                    log.info("玩家加入现有战斗: combatId={}, attackerId={}, enemyName={}",
                        existingCombatId, attackerId, enemyDisplayName);

                    return CombatResult.success(existingCombatId, existingCombatId, "加入战斗");
                }
            }

            // 检查玩家是否在敌人附近（九宫格范围内）
            int dx = Math.abs(attacker.getX() - targetEnemy.getX());
            int dy = Math.abs(attacker.getY() - targetEnemy.getY());
            if (dx > 1 || dy > 1) {
                return CombatResult.error("目标敌人不在交互范围内，请先移动到敌人附近");
            }

            // 获取敌人配置
            com.heibai.clawworld.infrastructure.config.data.character.EnemyConfig enemyConfig =
                configDataManager.getEnemy(targetEnemy.getTemplateId());
            if (enemyConfig == null) {
                return CombatResult.error("敌人配置不存在: " + targetEnemy.getTemplateId());
            }

            // 收集同格子同阵营的所有玩家
            List<Player> attackerParty = collectPartyMembers(attacker);

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

            // 更新所有参战玩家的战斗状态
            for (Player player : attackerParty) {
                Optional<PlayerEntity> playerEntityOpt = playerRepository.findById(player.getId());
                if (playerEntityOpt.isPresent()) {
                    PlayerEntity playerEntity = playerEntityOpt.get();
                    playerEntity.setInCombat(true);
                    playerEntity.setCombatId(combatId);
                    playerRepository.save(playerEntity);
                }
            }

            log.info("发起PVE战斗: combatId={}, attackerId={}, enemyName={}, mapId={}, enemyCount={}",
                combatId, attackerId, enemyDisplayName, mapId, enemyCombatChars.size());

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
            // 如果skillName就是技能ID，直接使用；否则需要从配置中查找
            String skillId = findSkillIdByName(skillName);

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
     * 处理战斗结束时的窗口状态转换和战利品分配
     * 将所有参战玩家的窗口状态从COMBAT转换回MAP
     * 注意：战斗结束后战斗实例可能已被移除，所以从数据库中查找参战玩家
     */
    private void handleCombatEndWindowTransition(String combatId) {
        try {
            // 获取战利品分配结果
            CombatInstance.RewardDistribution distribution = combatEngine.getAndRemoveRewardDistribution(combatId);

            // 处理战利品分配
            if (distribution != null) {
                distributeRewards(distribution);

                // 同步玩家的战斗后状态（生命和法力）
                syncPlayerFinalStates(distribution);

                // 更新被击败敌人的状态
                updateDefeatedEnemies(distribution);
            }

            // 从数据库中查找所有combatId匹配的玩家
            List<PlayerEntity> playersInCombat = playerRepository.findAll().stream()
                .filter(p -> combatId.equals(p.getCombatId()))
                .collect(Collectors.toList());

            if (playersInCombat.isEmpty()) {
                log.debug("没有找到参战玩家，可能战斗状态已被清理: combatId={}", combatId);
                return;
            }

            List<com.heibai.clawworld.domain.window.WindowTransition> transitions = new java.util.ArrayList<>();

            for (PlayerEntity player : playersInCombat) {
                String playerId = player.getId();
                String currentWindow = windowStateService.getCurrentWindowType(playerId);
                transitions.add(com.heibai.clawworld.domain.window.WindowTransition.of(
                    playerId, currentWindow, "MAP", null));

                // 清除玩家的战斗状态
                player.setInCombat(false);
                player.setCombatId(null);
                playerRepository.save(player);
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

    /**
     * 更新被击败敌人的状态
     * 根据设计文档：敌人被击败后短暂消失，然后根据刷新时间定时刷回来
     */
    private void updateDefeatedEnemies(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getDefeatedEnemies() == null) {
            return;
        }

        for (CombatInstance.DefeatedEnemy defeatedEnemy : distribution.getDefeatedEnemies()) {
            Optional<com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity> enemyOpt =
                enemyInstanceRepository.findByMapIdAndInstanceId(defeatedEnemy.getMapId(), defeatedEnemy.getInstanceId());

            if (enemyOpt.isPresent()) {
                var enemy = enemyOpt.get();
                enemy.setDead(true);
                enemy.setLastDeathTime(System.currentTimeMillis());
                enemy.setInCombat(false);
                enemy.setCombatId(null);
                enemyInstanceRepository.save(enemy);
                log.debug("敌人 {} 被击败，将在 {} 秒后刷新",
                    enemy.getDisplayName(), defeatedEnemy.getRespawnSeconds());
            }
        }
    }

    /**
     * 同步玩家的战斗后状态（生命和法力）
     * 根据设计文档：战斗胜利后玩家不会回复生命值和法力值
     */
    private void syncPlayerFinalStates(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getPlayerFinalStates() == null) {
            return;
        }

        for (Map.Entry<String, CombatInstance.PlayerFinalState> entry : distribution.getPlayerFinalStates().entrySet()) {
            String playerId = entry.getKey();
            CombatInstance.PlayerFinalState finalState = entry.getValue();

            Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
            if (playerOpt.isPresent()) {
                PlayerEntity player = playerOpt.get();
                player.setCurrentHealth(finalState.getCurrentHealth());
                player.setCurrentMana(finalState.getCurrentMana());
                playerRepository.save(player);
                log.debug("同步玩家 {} 战斗后状态: HP={}, MP={}",
                    player.getName(), finalState.getCurrentHealth(), finalState.getCurrentMana());
            }
        }
    }

    /**
     * 分配战利品
     * 根据设计文档：
     * - 每个玩家都获得全部经验
     * - 金钱平分
     * - 物品归队长
     */
    private void distributeRewards(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getPlayerIds() == null || distribution.getPlayerIds().isEmpty()) {
            return;
        }

        log.info("开始分配战利品: winnerFaction={}, exp={}, gold={}, items={}, players={}",
            distribution.getWinnerFactionId(),
            distribution.getTotalExperience(),
            distribution.getTotalGold(),
            distribution.getItems().size(),
            distribution.getPlayerIds().size());

        // 为每个玩家分配经验和金钱
        for (String playerId : distribution.getPlayerIds()) {
            Player player = playerSessionService.getPlayerState(playerId);
            if (player != null) {
                // 每个玩家都获得全部经验
                if (distribution.getTotalExperience() > 0) {
                    boolean leveledUp = playerLevelService.addExperienceAndCheckLevelUp(player, distribution.getTotalExperience());
                    log.debug("玩家 {} 获得经验: {}", player.getName(), distribution.getTotalExperience());

                    if (leveledUp) {
                        log.info("玩家 {} 升级到 {} 级！", player.getName(), player.getLevel());
                    }
                }

                // 金钱平分
                if (distribution.getGoldPerPlayer() > 0) {
                    player.setGold(player.getGold() + distribution.getGoldPerPlayer());
                    log.debug("玩家 {} 获得金钱: {}", player.getName(), distribution.getGoldPerPlayer());
                }

                // 保存玩家状态
                playerSessionService.savePlayerState(player);
            }
        }

        // 物品归队长
        if (distribution.getItems() != null && !distribution.getItems().isEmpty() && distribution.getLeaderId() != null) {
            Player leader = playerSessionService.getPlayerState(distribution.getLeaderId());
            if (leader != null) {
                for (String itemId : distribution.getItems()) {
                    // 检查是否已有该物品（只对普通物品堆叠）
                    boolean found = false;
                    if (configDataManager.getEquipment(itemId) == null) {
                        // 普通物品可以堆叠
                        for (Player.InventorySlot slot : leader.getInventory()) {
                            if (slot.isItem() && slot.getItem().getId().equals(itemId)) {
                                slot.setQuantity(slot.getQuantity() + 1);
                                found = true;
                                break;
                            }
                        }
                    }

                    // 如果没有找到或是装备，添加新的物品槽
                    if (!found && leader.getInventory().size() < 50) {
                        var eqConfig = configDataManager.getEquipment(itemId);
                        if (eqConfig != null) {
                            // 装备需要生成实例编号
                            // TODO: 实现装备实例编号生成逻辑
                            leader.getInventory().add(Player.InventorySlot.forEquipment(
                                configMapper.toDomain(eqConfig)));
                        } else {
                            var itemConfig = configDataManager.getItem(itemId);
                            if (itemConfig != null) {
                                leader.getInventory().add(Player.InventorySlot.forItem(
                                    configMapper.toDomain(itemConfig), 1));
                            }
                        }
                    }

                    log.debug("队长 {} 获得物品: {}", leader.getName(), itemId);
                }

                // 保存队长状态
                playerSessionService.savePlayerState(leader);
            }
        }

        log.info("战利品分配完成");
    }
}

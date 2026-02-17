package com.heibai.clawworld.domain.service;

import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.domain.combat.CombatTurnWaiter;
import com.heibai.clawworld.domain.service.ai.EnemyAI;
import com.heibai.clawworld.domain.service.ai.SimpleEnemyAI;
import com.heibai.clawworld.domain.skill.Skill;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.skill.SkillConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战斗引擎 - 管理所有活跃的战斗
 *
 * 核心职责：
 * 1. 管理所有活跃战斗实例
 * 2. 通过数学计算确定行动顺序（无需定时任务）
 * 3. 处理战斗结算
 * 4. 支持多方随时加入战斗
 * 5. 处理玩家指令的阻塞等待
 * 6. 自动触发敌人AI
 */
@Slf4j
@Component
public class CombatEngine {

    // 所有活跃的战斗实例
    private final Map<String, CombatInstance> activeCombats = new ConcurrentHashMap<>();

    // 每个战斗的回合等待器
    private final Map<String, CombatTurnWaiter> turnWaiters = new ConcurrentHashMap<>();

    // 伤害计算器
    private final CombatDamageCalculator damageCalculator = new CombatDamageCalculator();

    // 战利品计算器
    private final CombatRewardCalculator rewardCalculator = new CombatRewardCalculator();

    // 敌人AI（使用带配置的版本）
    private final EnemyAI enemyAI;

    // 配置数据管理器
    private final ConfigDataManager configDataManager;

    public CombatEngine(ConfigDataManager configDataManager) {
        this.configDataManager = configDataManager;
        this.enemyAI = new SimpleEnemyAI(configDataManager);
    }

    /**
     * 创建新战斗
     */
    public String createCombat(String mapId) {
        String combatId = UUID.randomUUID().toString();
        CombatInstance combat = new CombatInstance(combatId, mapId);
        activeCombats.put(combatId, combat);
        turnWaiters.put(combatId, new CombatTurnWaiter());
        log.info("创建战斗: combatId={}, mapId={}", combatId, mapId);
        return combatId;
    }

    /**
     * 获取战斗实例
     */
    public Optional<CombatInstance> getCombat(String combatId) {
        return Optional.ofNullable(activeCombats.get(combatId));
    }

    /**
     * 添加参战方到战斗
     */
    public void addPartyToCombat(String combatId, String factionId, List<CombatCharacter> characters) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            throw new IllegalArgumentException("战斗不存在: " + combatId);
        }

        CombatParty party = new CombatParty(factionId);
        for (CombatCharacter character : characters) {
            character.setFactionId(factionId);
            party.addCharacter(character);
        }

        combat.addParty(factionId, party);
        log.info("阵营 {} 加入战斗 {}", factionId, combatId);
    }

    /**
     * 添加角色到现有阵营
     * 根据设计文档：在战斗中，另一队玩家可以随时加入战斗
     */
    public void addCharacterToCombat(String combatId, String factionId, CombatCharacter character) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            throw new IllegalArgumentException("战斗不存在: " + combatId);
        }

        character.setFactionId(factionId);
        combat.addCharacterToParty(factionId, character);
        log.info("角色 {} 加入战斗 {} 的阵营 {}", character.getName(), combatId, factionId);
    }

    /**
     * 执行技能（带阻塞等待）
     * 这是玩家执行指令的主要入口
     *
     * 流程：
     * 1. 执行技能
     * 2. 循环处理敌人回合直到轮到玩家或战斗结束
     * 3. 返回期间的所有战斗日志
     */
    public CombatActionResult executeSkillWithWait(String combatId, String casterId, String skillId, String targetId) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            return CombatActionResult.error("战斗不存在");
        }

        CombatCharacter caster = combat.findCharacter(casterId);
        if (caster == null || !caster.isAlive()) {
            return CombatActionResult.error("施法者不存在或已死亡");
        }

        // 检查是否轮到该角色
        Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
        if (currentTurn.isEmpty() || !currentTurn.get().equals(casterId)) {
            return CombatActionResult.error("还未轮到你的回合");
        }

        // 执行技能
        CombatActionResult result = executeSkillInternal(combat, casterId, skillId, targetId);

        if (!result.isSuccess()) {
            return result;
        }

        // 如果战斗已结束，直接返回
        if (result.isCombatEnded()) {
            return result;
        }

        // 处理后续回合（敌人AI等）直到轮到玩家或战斗结束
        return processUntilPlayerTurnOrEnd(combat, casterId, result);
    }

    /**
     * 处理回合直到轮到指定玩家或战斗结束
     * 核心逻辑：循环处理敌人AI回合
     */
    private CombatActionResult processUntilPlayerTurnOrEnd(CombatInstance combat, String playerId, CombatActionResult result) {
        while (true) {
            // 检查战斗是否结束
            if (combat.isFinished()) {
                List<String> endLogs = finishCombat(combat);
                if (endLogs != null) {
                    result.setCombatEnded(true);
                    for (String log : endLogs) {
                        result.addLog(log);
                    }
                }
                return result;
            }

            // 检查战斗是否超时
            if (combat.isTimeout()) {
                handleCombatTimeout(combat);
                result.setCombatEnded(true);
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 获取下一个行动的角色
            Optional<String> nextTurnOpt = combat.getCurrentTurnCharacterId();
            if (nextTurnOpt.isEmpty()) {
                return result;
            }

            String nextCharacterId = nextTurnOpt.get();
            CombatCharacter nextCharacter = combat.findCharacter(nextCharacterId);

            if (nextCharacter == null || !nextCharacter.isAlive()) {
                combat.resetActionBar(nextCharacterId);
                continue;
            }

            // 记录回合开始
            combat.addLog("=== 轮到 " + nextCharacter.getName() + " 的回合 ===");

            // 如果是玩家回合，返回等待玩家输入
            if (nextCharacter.isPlayer()) {
                if (nextCharacterId.equals(playerId)) {
                    result.setMessage("轮到你的回合");
                    result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                    return result;
                } else {
                    // 其他玩家的回合，暂时跳过（多人战斗时需要处理）
                    combat.addLog(nextCharacter.getName() + " 等待行动...");
                    combat.resetActionBar(nextCharacterId);
                    continue;
                }
            }

            // 敌人回合，执行AI
            if (nextCharacter.isEnemy()) {
                executeEnemyAI(combat, nextCharacter, result);

                // 检查战斗是否因敌人行动而结束
                if (combat.isFinished()) {
                    List<String> endLogs = finishCombat(combat);
                    if (endLogs != null) {
                        result.setCombatEnded(true);
                        for (String log : endLogs) {
                            result.addLog(log);
                        }
                    }
                    return result;
                }
            }
        }
    }

    /**
     * 将日志条目转换为字符串列表
     */
    private List<String> convertLogsToStrings(List<CombatInstance.CombatLogEntry> logs) {
        return logs.stream()
            .map(entry -> String.format("[#%d] %s", entry.getSequence(), entry.getMessage()))
            .collect(Collectors.toList());
    }

    /**
     * 执行技能（内部方法，不阻塞）
     */
    private CombatActionResult executeSkillInternal(CombatInstance combat, String casterId, String skillId, String targetId) {
        CombatCharacter caster = combat.findCharacter(casterId);
        if (caster == null || !caster.isAlive()) {
            return CombatActionResult.error("施法者不存在或已死亡");
        }

        // 检查技能冷却
        if (caster.isSkillOnCooldown(skillId)) {
            return CombatActionResult.error("技能冷却中");
        }

        // 从技能配置中获取技能信息
        Skill skill = getSkillById(skillId);
        if (skill == null) {
            return CombatActionResult.error("技能不存在");
        }

        // 检查法力值
        if (!caster.consumeMana(skill.getManaCost())) {
            return CombatActionResult.error("法力值不足");
        }

        CombatActionResult result = new CombatActionResult();
        result.setSuccess(true);
        result.setBattleLog(new ArrayList<>());

        // 根据技能目标类型执行
        switch (skill.getTargetType()) {
            case SELF -> executeSelfSkill(combat, caster, skill, result);
            case ALLY_SINGLE -> executeAllySingleSkill(combat, caster, targetId, skill, result);
            case ALLY_ALL -> executeAllyAllSkill(combat, caster, skill, result);
            case ENEMY_SINGLE -> executeEnemySingleSkill(combat, caster, targetId, skill, result);
            case ENEMY_ALL -> executeEnemyAllSkill(combat, caster, skill, result);
        }

        // 设置技能冷却
        if (skill.getCooldown() > 0) {
            caster.setSkillCooldown(skillId, skill.getCooldown());
        }

        // 重置行动条（回合结束时才减少冷却）
        combat.resetActionBar(casterId);

        // 检查战斗是否结束
        List<String> endLogs = finishCombat(combat);
        if (endLogs != null) {
            result.setCombatEnded(true);
            // 将战斗结束时的新增日志（胜利信息和战利品）添加到结果中
            for (String log : endLogs) {
                result.addLog(log);
            }
        }

        return result;
    }

    /**
     * 执行对自身的技能
     */
    private void executeSelfSkill(CombatInstance combat, CombatCharacter caster, Skill skill, CombatActionResult result) {
        addBattleLog(combat, result, caster.getName() + " 对自己使用了 " + skill.getName());

        if (skill.getDamageType() == Skill.DamageType.NONE) {
            // 治疗或增益技能
            int healAmount = (int) (caster.getMagicAttack() * skill.getDamageMultiplier());
            caster.heal(healAmount);
            addBattleLog(combat, result, caster.getName() + " 恢复了 " + healAmount + " 点生命值");
        }
    }

    /**
     * 执行对友方单体的技能
     */
    private void executeAllySingleSkill(CombatInstance combat, CombatCharacter caster, String targetId, Skill skill, CombatActionResult result) {
        CombatCharacter target = combat.findCharacter(targetId);
        if (target == null || !target.isAlive()) {
            addBattleLog(combat, result, "目标不存在或已死亡");
            return;
        }

        addBattleLog(combat, result, caster.getName() + " 对 " + target.getName() + " 使用了 " + skill.getName());

        if (skill.getDamageType() == Skill.DamageType.NONE) {
            // 治疗技能
            int healAmount = (int) (caster.getMagicAttack() * skill.getDamageMultiplier());
            target.heal(healAmount);
            addBattleLog(combat, result, target.getName() + " 恢复了 " + healAmount + " 点生命值");
        }
    }

    /**
     * 执行对友方群体的技能
     */
    private void executeAllyAllSkill(CombatInstance combat, CombatCharacter caster, Skill skill, CombatActionResult result) {
        List<CombatCharacter> allies = combat.getAliveCharactersInFaction(caster.getFactionId());

        addBattleLog(combat, result, caster.getName() + " 使用了 " + skill.getName());

        for (CombatCharacter ally : allies) {
            if (skill.getDamageType() == Skill.DamageType.NONE) {
                int healAmount = (int) (caster.getMagicAttack() * skill.getDamageMultiplier());
                ally.heal(healAmount);
                addBattleLog(combat, result, ally.getName() + " 恢复了 " + healAmount + " 点生命值");
            }
        }
    }

    /**
     * 执行对敌方单体的技能
     */
    private void executeEnemySingleSkill(CombatInstance combat, CombatCharacter caster, String targetId, Skill skill, CombatActionResult result) {
        CombatCharacter target = combat.findCharacter(targetId);
        if (target == null || !target.isAlive()) {
            addBattleLog(combat, result, "目标不存在或已死亡");
            return;
        }

        addBattleLog(combat, result, caster.getName() + " 对 " + target.getName() + " 使用了 " + skill.getName());

        boolean isPhysical = skill.getDamageType() == Skill.DamageType.PHYSICAL;
        CombatDamageCalculator.DamageResult damageResult = damageCalculator.calculateDamage(
            caster, target, isPhysical, skill.getDamageMultiplier()
        );

        if (damageResult.isMissed()) {
            addBattleLog(combat, result, "攻击未命中！");
        } else {
            target.takeDamage(damageResult.getDamage());
            combat.recordDamage(caster.getFactionId(), target.getCharacterId(), damageResult.getDamage());

            String damageLog = String.format("造成了 %d 点伤害", damageResult.getDamage());
            if (damageResult.isCrit()) {
                damageLog += "（暴击！）";
            }
            addBattleLog(combat, result, damageLog);

            if (!target.isAlive()) {
                addBattleLog(combat, result, target.getName() + " 被击败了！");
            }
        }
    }

    /**
     * 执行对敌方群体的技能
     */
    private void executeEnemyAllSkill(CombatInstance combat, CombatCharacter caster, Skill skill, CombatActionResult result) {
        List<CombatCharacter> enemies = combat.getEnemyCharacters(caster.getFactionId());

        addBattleLog(combat, result, caster.getName() + " 使用了 " + skill.getName());

        boolean isPhysical = skill.getDamageType() == Skill.DamageType.PHYSICAL;

        for (CombatCharacter enemy : enemies) {
            CombatDamageCalculator.DamageResult damageResult = damageCalculator.calculateDamage(
                caster, enemy, isPhysical, skill.getDamageMultiplier()
            );

            if (damageResult.isMissed()) {
                addBattleLog(combat, result, "对 " + enemy.getName() + " 的攻击未命中！");
            } else {
                enemy.takeDamage(damageResult.getDamage());
                combat.recordDamage(caster.getFactionId(), enemy.getCharacterId(), damageResult.getDamage());

                String damageLog = String.format("对 %s 造成了 %d 点伤害", enemy.getName(), damageResult.getDamage());
                if (damageResult.isCrit()) {
                    damageLog += "（暴击！）";
                }
                addBattleLog(combat, result, damageLog);

                if (!enemy.isAlive()) {
                    addBattleLog(combat, result, enemy.getName() + " 被击败了！");
                }
            }
        }
    }

    /**
     * 添加战斗日志（同时添加到战斗实例和结果中）
     */
    private void addBattleLog(CombatInstance combat, CombatActionResult result, String message) {
        combat.addLog(message);
        result.addLog(message);
    }

    /**
     * 跳过回合（带后续处理）
     */
    public CombatActionResult skipTurnWithWait(String combatId, String characterId) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            return CombatActionResult.error("战斗不存在");
        }

        CombatCharacter character = combat.findCharacter(characterId);
        if (character == null) {
            return CombatActionResult.error("角色不存在");
        }

        Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
        if (currentTurn.isEmpty() || !currentTurn.get().equals(characterId)) {
            return CombatActionResult.error("还未轮到你的回合");
        }

        // 跳过回合
        CombatActionResult result = skipTurnInternal(combat, characterId);

        if (result.isCombatEnded()) {
            return result;
        }

        // 处理后续回合直到轮到玩家或战斗结束
        return processUntilPlayerTurnOrEnd(combat, characterId, result);
    }

    /**
     * 跳过回合（内部方法）
     */
    private CombatActionResult skipTurnInternal(CombatInstance combat, String characterId) {
        CombatCharacter character = combat.findCharacter(characterId);
        if (character == null) {
            return CombatActionResult.error("角色不存在");
        }

        combat.addLog(character.getName() + " 跳过了回合");

        // 重置行动条（会自动减少冷却）
        combat.resetActionBar(characterId);

        CombatActionResult result = CombatActionResult.success("跳过回合");

        List<String> endLogs = finishCombat(combat);
        if (endLogs != null) {
            result.setCombatEnded(true);
            for (String log : endLogs) {
                result.addLog(log);
            }
        }

        return result;
    }

    /**
     * 跳过回合（旧接口，保持兼容）
     */
    public CombatActionResult skipTurn(String combatId, String characterId) {
        return skipTurnWithWait(combatId, characterId);
    }

    /**
     * 逃离战斗
     */
    public CombatActionResult forfeit(String combatId, String characterId) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            return CombatActionResult.error("战斗不存在");
        }

        CombatCharacter character = combat.findCharacter(characterId);
        if (character == null) {
            return CombatActionResult.error("角色不存在");
        }

        character.setDead(true);
        character.setCurrentHealth(0);
        combat.addLog(character.getName() + " 逃离了战斗");

        CombatActionResult result = CombatActionResult.success("逃离战斗");

        List<String> endLogs = finishCombat(combat);
        if (endLogs != null) {
            result.setCombatEnded(true);
            for (String log : endLogs) {
                result.addLog(log);
            }
        }

        return result;
    }

    /**
     * 执行敌人AI
     */
    private void executeEnemyAI(CombatInstance combat, CombatCharacter enemy, CombatActionResult result) {
        try {
            log.debug("敌人 {} 开始AI决策", enemy.getName());

            // AI决策
            EnemyAI.AIDecision decision = enemyAI.makeDecision(combat, enemy);

            if (decision.getType() == EnemyAI.DecisionType.ATTACK) {
                // 执行攻击
                CombatActionResult aiResult = executeSkillInternal(combat, enemy.getCharacterId(), decision.getSkillId(), decision.getTargetId());
                // 合并日志
                if (aiResult.getBattleLog() != null) {
                    for (String log : aiResult.getBattleLog()) {
                        result.addLog(log);
                    }
                }
            } else {
                // 跳过回合
                skipTurnInternal(combat, enemy.getCharacterId());
            }
        } catch (Exception e) {
            log.error("敌人AI执行失败", e);
            // 出错时跳过回合
            skipTurnInternal(combat, enemy.getCharacterId());
        }
    }

    /**
     * 处理战斗超时
     * 根据设计文档：
     * - 玩家vs玩家超时：不分胜负
     * - 玩家vs敌人超时：视同全部玩家死亡
     */
    private void handleCombatTimeout(CombatInstance combat) {
        String combatId = combat.getCombatId();
        combat.setStatus(com.heibai.clawworld.domain.combat.Combat.CombatStatus.TIMEOUT);
        combat.addLog("战斗超时！");

        if (combat.getCombatType() == CombatInstance.CombatType.PVE) {
            combat.addLog("PVE战斗超时，所有玩家视为死亡");

            for (CombatParty party : combat.getParties().values()) {
                for (CombatCharacter character : party.getCharacters()) {
                    if (character.isPlayer()) {
                        character.setDead(true);
                        character.setCurrentHealth(0);
                        combat.addLog(character.getName() + " 因超时死亡");
                    }
                }
            }
        } else if (combat.getCombatType() == CombatInstance.CombatType.PVP) {
            combat.addLog("PVP战斗超时，不分胜负");
        }

        // 通知所有等待的玩家
        CombatTurnWaiter waiter = turnWaiters.get(combatId);
        if (waiter != null) {
            waiter.notifyAllWaiting();
        }

        activeCombats.remove(combatId);
        turnWaiters.remove(combatId);
        log.info("战斗超时: combatId={}, type={}", combatId, combat.getCombatType());
    }

    // 战利品分配结果缓存（key: combatId, value: 分配结果）
    // 战斗结束后保存，供CombatService处理持久化
    private final Map<String, CombatInstance.RewardDistribution> rewardDistributionCache = new ConcurrentHashMap<>();

    /**
     * 获取并移除战利品分配结果
     * 由CombatService在战斗结束后调用，用于持久化战利品
     */
    public CombatInstance.RewardDistribution getAndRemoveRewardDistribution(String combatId) {
        return rewardDistributionCache.remove(combatId);
    }

    /**
     * 结束战斗并处理奖励
     * @return 战斗结束时新增的日志列表，如果战斗未结束返回null
     */
    private List<String> finishCombat(CombatInstance combat) {
        if (!combat.isFinished()) {
            return null;
        }

        String combatId = combat.getCombatId();
        combat.setStatus(com.heibai.clawworld.domain.combat.Combat.CombatStatus.FINISHED);

        // 记录当前日志数量，用于获取新增的日志
        int logCountBefore = combat.getAllLogs().size();

        Optional<CombatParty> winner = combat.getWinner();
        if (winner.isPresent()) {
            combat.addLog("阵营 " + winner.get().getFactionId() + " 获得胜利！");
            handleCombatRewards(combat, winner.get());
        } else {
            combat.addLog("战斗平局！");
        }

        // 保存战利品分配结果到缓存
        if (combat.getRewardDistribution() != null) {
            rewardDistributionCache.put(combatId, combat.getRewardDistribution());
        }

        // 获取新增的日志
        List<CombatInstance.CombatLogEntry> allLogs = combat.getAllLogs();
        List<String> newLogs = new ArrayList<>();
        for (int i = logCountBefore; i < allLogs.size(); i++) {
            CombatInstance.CombatLogEntry entry = allLogs.get(i);
            newLogs.add(String.format("[#%d] %s", entry.getSequence(), entry.getMessage()));
        }

        // 通知所有等待的玩家
        CombatTurnWaiter waiter = turnWaiters.get(combatId);
        if (waiter != null) {
            waiter.notifyAllWaiting();
        }

        activeCombats.remove(combatId);
        turnWaiters.remove(combatId);
        log.info("战斗结束: combatId={}", combatId);
        return newLogs;
    }

    /**
     * 处理战斗奖励
     * 根据设计文档：
     * - 战利品归属于对敌人造成最后攻击的队伍
     * - 组队中战胜敌人，每个玩家都获得全部经验
     * - 金钱平分
     * - 物品归队长持有
     * - 战斗结束后玩家不会回复生命值和法力值
     */
    private void handleCombatRewards(CombatInstance combat, CombatParty winner) {
        log.info("战斗 {} 的胜利方 {} 获得战利品", combat.getCombatId(), winner.getFactionId());

        // 计算总战利品
        int totalExp = 0;
        int totalGold = 0;
        List<String> droppedItems = new ArrayList<>();

        // 遍历所有被击败的敌人，计算战利品
        for (CombatParty party : combat.getParties().values()) {
            if (party.getFactionId().equals(winner.getFactionId())) {
                continue; // 跳过胜利方
            }
            for (CombatCharacter character : party.getCharacters()) {
                if (!character.isAlive() && character.isEnemy()) {
                    // 计算这个敌人的战利品
                    CombatRewardCalculator.CombatReward reward = rewardCalculator.calculateEnemyReward(character, winner.getFactionId());
                    totalExp += reward.getExperience();
                    totalGold += reward.getGold();
                    if (reward.getItems() != null) {
                        droppedItems.addAll(reward.getItems());
                    }
                }
            }
        }

        // 获取胜利方的所有存活玩家
        List<CombatCharacter> winnerPlayers = winner.getCharacters().stream()
            .filter(c -> c.isPlayer() && c.isAlive())
            .collect(Collectors.toList());

        // 创建战利品分配结果
        CombatInstance.RewardDistribution distribution = new CombatInstance.RewardDistribution();
        distribution.setWinnerFactionId(winner.getFactionId());

        // 保存所有玩家的最终状态（包括所有参战方的玩家）
        for (CombatParty party : combat.getParties().values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.isPlayer()) {
                    distribution.getPlayerFinalStates().put(
                        character.getCharacterId(),
                        new CombatInstance.PlayerFinalState(character.getCurrentHealth(), character.getCurrentMana())
                    );
                }
                // 记录被击败的敌人
                if (character.isEnemy() && !character.isAlive()) {
                    if (character.getEnemyMapId() != null && character.getEnemyInstanceId() != null) {
                        distribution.getDefeatedEnemies().add(
                            new CombatInstance.DefeatedEnemy(
                                character.getEnemyMapId(),
                                character.getEnemyInstanceId(),
                                character.getEnemyRespawnSeconds()
                            )
                        );
                    }
                }
            }
        }

        if (winnerPlayers.isEmpty()) {
            // 没有存活的玩家，不分配战利品
            combat.addLog("=== 战利品 ===");
            combat.addLog("没有存活的玩家，战利品无法分配");
            combat.setRewardDistribution(distribution);
            return;
        }

        // 计算分配
        int playerCount = winnerPlayers.size();
        int goldPerPlayer = totalGold / playerCount;

        // 找到队长（第一个玩家视为队长，或者有partyLeader标记的）
        CombatCharacter leader = winnerPlayers.stream()
            .filter(CombatCharacter::isPartyLeader)
            .findFirst()
            .orElse(winnerPlayers.get(0));

        distribution.setTotalExperience(totalExp);
        distribution.setTotalGold(totalGold);
        distribution.setGoldPerPlayer(goldPerPlayer);
        distribution.setItems(droppedItems);
        distribution.setLeaderId(leader.getCharacterId());
        distribution.setPlayerIds(winnerPlayers.stream()
            .map(CombatCharacter::getCharacterId)
            .collect(Collectors.toList()));
        combat.setRewardDistribution(distribution);

        // 添加战利品日志
        combat.addLog("=== 战利品分配 ===");

        if (totalExp > 0) {
            if (playerCount == 1) {
                combat.addLog(leader.getName() + " 获得经验: " + totalExp);
            } else {
                combat.addLog("每人获得经验: " + totalExp + " (共" + playerCount + "人)");
            }
        }

        if (totalGold > 0) {
            if (playerCount == 1) {
                combat.addLog(leader.getName() + " 获得金钱: " + totalGold);
            } else {
                combat.addLog("金钱平分: 每人 " + goldPerPlayer + " (总计" + totalGold + ")");
            }
        }

        if (!droppedItems.isEmpty()) {
            for (String itemId : droppedItems) {
                // 尝试获取物品名称
                String itemName = itemId;
                if (configDataManager != null) {
                    var itemConfig = configDataManager.getItem(itemId);
                    if (itemConfig != null) {
                        itemName = itemConfig.getName();
                    } else {
                        var equipConfig = configDataManager.getEquipment(itemId);
                        if (equipConfig != null) {
                            itemName = equipConfig.getName();
                        }
                    }
                }
                combat.addLog(leader.getName() + " 获得物品: " + itemName);
            }
        }

        if (totalExp == 0 && totalGold == 0 && droppedItems.isEmpty()) {
            combat.addLog("没有获得任何战利品");
        }
    }

    /**
     * 获取技能信息
     * 从配置管理器中获取技能配置并转换为领域对象
     */
    private Skill getSkillById(String skillId) {
        // 处理普通攻击
        if ("普通攻击".equals(skillId) || "basic_attack".equals(skillId)) {
            Skill skill = new Skill();
            skill.setId("basic_attack");
            skill.setName("普通攻击");
            skill.setTargetType(Skill.SkillTarget.ENEMY_SINGLE);
            skill.setDamageType(Skill.DamageType.PHYSICAL);
            skill.setDamageMultiplier(1.0);
            skill.setManaCost(0);
            skill.setCooldown(0);
            return skill;
        }

        // 从配置中获取技能
        SkillConfig config = configDataManager.getSkill(skillId);
        if (config == null) {
            return null;
        }

        // 转换为领域对象
        Skill skill = new Skill();
        skill.setId(config.getId());
        skill.setName(config.getName());
        skill.setDescription(config.getDescription());
        skill.setTargetType(Skill.SkillTarget.valueOf(config.getTargetType()));
        skill.setDamageType(Skill.DamageType.valueOf(config.getDamageType()));
        skill.setManaCost(config.getManaCost());
        skill.setCooldown(config.getCooldown());
        skill.setDamageMultiplier(config.getDamageMultiplier());
        return skill;
    }

    /**
     * 战斗行动结果
     */
    @Data
    public static class CombatActionResult {
        private boolean success;
        private String message;
        private List<String> battleLog;
        private boolean combatEnded;

        public static CombatActionResult success(String message) {
            CombatActionResult result = new CombatActionResult();
            result.setSuccess(true);
            result.setMessage(message);
            result.setBattleLog(new ArrayList<>());
            return result;
        }

        public static CombatActionResult error(String message) {
            CombatActionResult result = new CombatActionResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }

        public void addLog(String log) {
            if (battleLog == null) {
                battleLog = new ArrayList<>();
            }
            battleLog.add(log);
        }
    }
}

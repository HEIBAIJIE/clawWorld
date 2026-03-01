package com.heibai.clawworld.domain.service;

import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.domain.combat.CombatTurnWaiter;
import com.heibai.clawworld.domain.combat.TurnTimeoutManager;
import com.heibai.clawworld.domain.service.ai.EnemyAI;
import com.heibai.clawworld.domain.service.ai.SimpleEnemyAI;
import com.heibai.clawworld.domain.service.skill.SkillExecutor;
import com.heibai.clawworld.domain.service.skill.SkillResolver;
import com.heibai.clawworld.domain.skill.Skill;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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
 * 7. 管理玩家回合超时（10秒自动空过）
 */
@Slf4j
@Component
public class CombatEngine {

    // 所有活跃的战斗实例
    private final Map<String, CombatInstance> activeCombats = new ConcurrentHashMap<>();

    // 每个战斗的回合等待器
    @Getter
    private final Map<String, CombatTurnWaiter> turnWaiters = new ConcurrentHashMap<>();

    // 每个战斗的锁（确保同一战斗的操作是串行的）
    private final Map<String, ReentrantLock> combatLocks = new ConcurrentHashMap<>();

    // 敌人AI（使用带配置的版本）
    private final EnemyAI enemyAI;

    // 技能解析器
    private final SkillResolver skillResolver;

    // 技能执行器
    private final SkillExecutor skillExecutor;

    // 回合超时管理器
    private final TurnTimeoutManager turnTimeoutManager;

    // 战斗结算服务
    private final CombatSettlementService settlementService;

    public CombatEngine(ConfigDataManager configDataManager, SkillResolver skillResolver,
                        SkillExecutor skillExecutor, CombatSettlementService settlementService) {
        this.skillResolver = skillResolver;
        this.skillExecutor = skillExecutor;
        this.settlementService = settlementService;
        this.enemyAI = new SimpleEnemyAI(configDataManager);
        this.turnTimeoutManager = new TurnTimeoutManager(this::handleTurnTimeout);
    }

    @PreDestroy
    public void destroy() {
        turnTimeoutManager.shutdown();
    }

    /**
     * 获取战斗锁（如果不存在则创建）
     */
    private ReentrantLock getCombatLock(String combatId) {
        return combatLocks.computeIfAbsent(combatId, k -> new ReentrantLock());
    }

    /**
     * 处理回合超时回调
     * 当玩家回合超时时，自动执行空过
     */
    private void handleTurnTimeout(String combatId, String characterId) {
        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            CombatInstance combat = activeCombats.get(combatId);
            if (combat == null) {
                return;
            }

            // 检查是否确实轮到该角色（双重检查，防止竞态条件）
            Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
            if (currentTurn.isEmpty() || !currentTurn.get().equals(characterId)) {
                return;
            }

            CombatCharacter character = combat.findCharacter(characterId);
            if (character == null || !character.isAlive()) {
                return;
            }

            log.info("[战斗 {}] 玩家 {} 回合超时，自动空过", combatId, character.getName());

            // 记录超时空过日志
            combat.addLog(character.getName() + " 回合超时，自动空过");

            // 执行空过
            skipTurnInternal(combat, characterId);

            // 通知所有等待的玩家
            CombatTurnWaiter waiter = turnWaiters.get(combatId);
            if (waiter != null) {
                waiter.notifyAllWaiting();
            }

            // 继续处理后续回合（敌人AI等）
            processNextTurnsAfterTimeoutInternal(combat);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建从活跃战斗中移除的回调
     */
    private Runnable createRemover(String combatId) {
        return () -> activeCombats.remove(combatId);
    }

    /**
     * 超时后继续处理后续回合（内部方法，已持有锁）
     * 这个方法会处理敌人AI回合，直到轮到下一个玩家或战斗结束
     */
    private void processNextTurnsAfterTimeoutInternal(CombatInstance combat) {
        while (true) {
            // 检查战斗是否结束
            if (combat.isFinished()) {
                settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
                return;
            }

            // 推进行动条
            combat.advanceToNextTurn();

            // 获取下一个行动的角色
            Optional<String> nextTurnOpt = combat.getCurrentTurnCharacterId();
            if (nextTurnOpt.isEmpty()) {
                return;
            }

            String nextCharacterId = nextTurnOpt.get();
            CombatCharacter nextCharacter = combat.findCharacter(nextCharacterId);

            if (nextCharacter == null || !nextCharacter.isAlive()) {
                combat.resetActionBar(nextCharacterId);
                continue;
            }

            // 记录回合开始
            combat.addLog("=== 轮到 " + nextCharacter.getName() + " 的回合 ===");

            // 如果是玩家回合，启动超时计时并返回
            if (nextCharacter.isPlayer()) {
                turnTimeoutManager.startPlayerTurn(combat.getCombatId(), nextCharacterId);

                // 通知该玩家
                CombatTurnWaiter waiter = turnWaiters.get(combat.getCombatId());
                if (waiter != null) {
                    waiter.notifyTurn(nextCharacterId);
                }
                return;
            }

            // 敌人回合，执行AI
            if (nextCharacter.isEnemy()) {
                executeEnemyAIInternal(combat, nextCharacter);

                // 检查战斗是否结束
                if (combat.isFinished()) {
                    settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
                    return;
                }
            }
        }
    }

    /**
     * 创建新战斗
     */
    public String createCombat(String mapId) {
        String combatId = UUID.randomUUID().toString();
        CombatInstance combat = new CombatInstance(combatId, mapId);
        activeCombats.put(combatId, combat);
        turnWaiters.put(combatId, new CombatTurnWaiter());
        combatLocks.put(combatId, new ReentrantLock());
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

        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            CombatParty party = new CombatParty(factionId);
            for (CombatCharacter character : characters) {
                character.setFactionId(factionId);
                party.addCharacter(character);
            }

            combat.addParty(factionId, party);
            log.info("阵营 {} 加入战斗 {}", factionId, combatId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 初始化战斗的第一个回合
     * 在所有参战方加入后调用，推进行动条并启动第一个玩家的超时计时
     */
    public void initializeFirstTurn(String combatId) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            return;
        }

        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            // 推进行动条到第一个回合
            combat.advanceToNextTurn();

            // 获取第一个行动的角色
            Optional<String> firstTurnOpt = combat.getCurrentTurnCharacterId();
            if (firstTurnOpt.isEmpty()) {
                return;
            }

            String firstCharacterId = firstTurnOpt.get();
            CombatCharacter firstCharacter = combat.findCharacter(firstCharacterId);

            if (firstCharacter == null || !firstCharacter.isAlive()) {
                return;
            }

            // 如果第一个行动的是玩家，启动超时计时
            if (firstCharacter.isPlayer()) {
                turnTimeoutManager.startPlayerTurn(combatId, firstCharacterId);
                log.debug("[战斗 {}] 第一个回合: 玩家 {} 开始行动", combatId, firstCharacter.getName());
            } else if (firstCharacter.isEnemy()) {
                // 如果第一个行动的是敌人，执行AI并继续处理
                combat.addLog("=== 轮到 " + firstCharacter.getName() + " 的回合 ===");
                executeEnemyAIInternal(combat, firstCharacter);

                // 继续处理后续回合直到轮到玩家
                processNextTurnsAfterTimeoutInternal(combat);
            }
        } finally {
            lock.unlock();
        }
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
     * 1. 检查战斗是否已结束（返回缓存的结束信息）
     * 2. 获取战斗锁，确保操作的原子性
     * 3. 检查是否轮到该玩家（不推进行动条，只查询）
     * 4. 执行技能
     * 5. 取消该玩家的计时
     * 6. 通知所有等待的玩家
     * 7. 循环处理敌人回合直到轮到玩家或战斗结束
     * 8. 返回期间的所有战斗日志
     */
    public CombatActionResult executeSkillWithWait(String combatId, String casterId, String skillId, String targetId) {
        // 首先检查战斗是否已结束
        Optional<CombatSettlementService.CombatEndInfo> endInfoOpt = settlementService.getEndedCombatInfo(combatId);
        if (endInfoOpt.isPresent()) {
            return settlementService.createCombatEndedResult(endInfoOpt.get(), casterId);
        }

        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            // 再次检查缓存（可能刚刚结束）
            endInfoOpt = settlementService.getEndedCombatInfo(combatId);
            if (endInfoOpt.isPresent()) {
                return settlementService.createCombatEndedResult(endInfoOpt.get(), casterId);
            }
            return CombatActionResult.error("战斗不存在");
        }

        // 获取战斗锁
        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            // 再次检查战斗是否存在（可能在等待锁期间被移除）
            combat = activeCombats.get(combatId);
            if (combat == null) {
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), casterId);
                }
                return CombatActionResult.error("战斗不存在");
            }

            CombatCharacter caster = combat.findCharacter(casterId);
            if (caster == null || !caster.isAlive()) {
                log.info("[战斗 {}] 玩家 {} 已死亡或不存在，检查战斗状态", combatId, casterId);
                // 玩家已死亡，检查战斗是否已结束
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    log.info("[战斗 {}] 从缓存中找到战斗结束信息", combatId);
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), casterId);
                }
                // 检查战斗实例是否已标记为结束
                boolean isFinished = combat.isFinished();
                log.info("[战斗 {}] combat.isFinished() = {}", combatId, isFinished);
                if (isFinished) {
                    // 战斗已结束，调用finishCombat进行结算
                    log.info("[战斗 {}] 战斗已结束，调用finishCombat进行结算", combatId);
                    List<String> endLogs = settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combatId));
                    CombatActionResult result = CombatActionResult.error("施法者不存在或已死亡");
                    result.setCombatEnded(true);
                    result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                    log.info("[战斗 {}] 返回战斗结束结果，combatEnded={}", combatId, result.isCombatEnded());
                    return result;
                }
                // 战斗未结束但玩家已死亡（不应该发生，但作为兜底）
                log.warn("[战斗 {}] 战斗未结束但玩家已死亡", combatId);
                CombatActionResult result = CombatActionResult.error("施法者不存在或已死亡");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 检查是否轮到该角色（不推进行动条，只查询当前状态）
            Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
            if (currentTurn.isEmpty() || !currentTurn.get().equals(casterId)) {
                // 不是该玩家的回合，返回当前战斗状态但拒绝执行
                CombatActionResult result = CombatActionResult.error("还未轮到你的回合");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 玩家行动，取消超时计时
            turnTimeoutManager.playerActed(combatId, casterId);

            // 执行技能
            CombatActionResult result = executeSkillInternal(combat, casterId, skillId, targetId);

            if (!result.isSuccess()) {
                return result;
            }

            // 如果战斗已结束，直接返回
            if (result.isCombatEnded()) {
                return result;
            }

            // 通知所有等待的玩家，让他们重新检查是否轮到自己
            CombatTurnWaiter waiter = turnWaiters.get(combatId);
            if (waiter != null) {
                waiter.notifyAllWaiting();
            }

            // 处理后续回合（敌人AI等）直到轮到玩家或战斗结束
            return processUntilPlayerTurnOrEndInternal(combat, casterId, result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清理过期的战斗结束缓存
     */
    private void cleanExpiredEndedCombatCache() {
        settlementService.cleanExpiredEndedCombatCache();
    }

    /**
     * 处理回合直到轮到指定玩家或战斗结束（内部方法，已持有锁）
     * 核心逻辑：
     * 1. 如果轮到当前玩家，启动超时计时并返回
     * 2. 如果轮到其他玩家，启动该玩家的超时计时，当前玩家立即返回（不阻塞）
     * 3. 如果轮到敌人，执行AI后继续循环
     */
    private CombatActionResult processUntilPlayerTurnOrEndInternal(CombatInstance combat, String playerId, CombatActionResult result) {
        CombatTurnWaiter waiter = turnWaiters.get(combat.getCombatId());

        while (true) {
            // 检查战斗是否结束
            if (combat.isFinished()) {
                List<String> endLogs = settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
                result.setCombatEnded(true);
                if (endLogs != null) {
                    for (String log : endLogs) {
                        result.addLog(log);
                    }
                }
                // 即使endLogs为null（战斗已经被处理过），也要设置战斗日志
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 检查战斗是否超时
            if (combat.isTimeout()) {
                settlementService.handleCombatTimeout(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
                result.setCombatEnded(true);
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 推进行动条到下一个回合
            combat.advanceToNextTurn();

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

            // 如果是玩家回合
            if (nextCharacter.isPlayer()) {
                // 启动该玩家的回合超时计时
                turnTimeoutManager.startPlayerTurn(combat.getCombatId(), nextCharacterId);

                if (nextCharacterId.equals(playerId)) {
                    // 轮到当前玩家，返回等待输入
                    result.setMessage("轮到你的回合");
                    result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                    return result;
                } else {
                    // 轮到其他玩家，记录日志并立即返回（不阻塞）
                    combat.addLog(nextCharacter.getName() + " 等待行动...");

                    // 通知轮到回合的玩家
                    if (waiter != null) {
                        waiter.notifyTurn(nextCharacterId);
                    }

                    // 当前玩家立即返回，告知需要等待
                    result.setMessage("未轮到你的回合，请输入wait继续等待");
                    result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                    return result;
                }
            }

            // 敌人回合，执行AI
            if (nextCharacter.isEnemy()) {
                executeEnemyAIInternal(combat, nextCharacter);

                // 检查战斗是否因敌人行动而结束
                if (combat.isFinished()) {
                    List<String> endLogs = settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
                    result.setCombatEnded(true);
                    if (endLogs != null) {
                        for (String log : endLogs) {
                            result.addLog(log);
                        }
                    }
                    // 即使endLogs为null（战斗已经被处理过），也要设置战斗日志
                    result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                    return result;
                }
            }
        }
    }

    /**
     * 执行敌人AI（内部方法，不带result参数）
     */
    private void executeEnemyAIInternal(CombatInstance combat, CombatCharacter enemy) {
        try {
            log.debug("敌人 {} 开始AI决策", enemy.getName());

            // AI决策
            EnemyAI.AIDecision decision = enemyAI.makeDecision(combat, enemy);

            if (decision.getType() == EnemyAI.DecisionType.ATTACK) {
                // 执行攻击
                executeSkillInternal(combat, enemy.getCharacterId(), decision.getSkillId(), decision.getTargetId());
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
     * 处理行动后的后续回合（供外部调用，如使用物品后）
     * 通知所有等待的玩家，然后处理后续回合直到轮到指定玩家或战斗结束
     */
    public CombatActionResult processAfterAction(String combatId, String playerId) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            return CombatActionResult.error("战斗不存在");
        }

        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            // 再次检查战斗是否存在
            combat = activeCombats.get(combatId);
            if (combat == null) {
                return CombatActionResult.error("战斗不存在");
            }

            // 通知所有等待的玩家，让他们重新检查是否轮到自己
            CombatTurnWaiter waiter = turnWaiters.get(combatId);
            if (waiter != null) {
                waiter.notifyAllWaiting();
            }

            CombatActionResult result = CombatActionResult.success("行动完成");
            result.setBattleLog(new ArrayList<>());

            // 处理后续回合直到轮到玩家或战斗结束
            return processUntilPlayerTurnOrEndInternal(combat, playerId, result);
        } finally {
            lock.unlock();
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
        log.debug("[战斗 {}] executeSkillInternal - 施法者={} 技能={} 目标={}",
            combat.getCombatId(), casterId, skillId, targetId);

        CombatCharacter caster = combat.findCharacter(casterId);
        if (caster == null || !caster.isAlive()) {
            CombatActionResult result = CombatActionResult.error("施法者不存在或已死亡");
            result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
            return result;
        }

        // 检查技能冷却
        if (caster.isSkillOnCooldown(skillId)) {
            CombatActionResult result = CombatActionResult.error("技能冷却中");
            result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
            return result;
        }

        // 从技能配置中获取技能信息
        Skill skill = skillResolver.getSkillById(skillId);
        if (skill == null) {
            CombatActionResult result = CombatActionResult.error("技能不存在");
            result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
            return result;
        }

        // 检查法力值
        if (!caster.consumeMana(skill.getManaCost())) {
            CombatActionResult result = CombatActionResult.error("法力值不足");
            result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
            return result;
        }

        CombatActionResult result = new CombatActionResult();
        result.setSuccess(true);
        result.setBattleLog(new ArrayList<>());

        // 使用SkillExecutor执行技能
        SkillExecutor.SkillExecutionResult execResult = skillExecutor.executeSkill(combat, caster, skill, targetId);
        for (String logMsg : execResult.getLogs()) {
            addBattleLog(combat, result, logMsg);
        }

        // 设置技能冷却
        if (skill.getCooldown() > 0) {
            caster.setSkillCooldown(skillId, skill.getCooldown());
        }

        log.debug("[战斗 {}] executeSkillInternal - 准备重置行动条: {}", combat.getCombatId(), casterId);
        // 重置行动条（回合结束时才减少冷却）
        combat.resetActionBar(casterId);
        log.debug("[战斗 {}] executeSkillInternal - 行动条重置完成", combat.getCombatId());

        // 检查战斗是否结束
        List<String> endLogs = settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
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
     * 添加战斗日志（同时添加到战斗实例和结果中）
     */
    private void addBattleLog(CombatInstance combat, CombatActionResult result, String message) {
        combat.addLog(message);
        result.addLog(message);
    }

    /**
     * 跳过回合（带后续处理）
     * 这个方法也用于玩家在非自己回合时发送wait指令等待
     */
    public CombatActionResult skipTurnWithWait(String combatId, String characterId) {
        // 首先检查战斗是否已结束
        Optional<CombatSettlementService.CombatEndInfo> endInfoOpt = settlementService.getEndedCombatInfo(combatId);
        if (endInfoOpt.isPresent()) {
            return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
        }

        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            // 再次检查缓存（可能刚刚结束）
            endInfoOpt = settlementService.getEndedCombatInfo(combatId);
            if (endInfoOpt.isPresent()) {
                return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
            }
            return CombatActionResult.error("战斗不存在");
        }

        // 获取战斗锁
        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            // 再次检查战斗是否存在
            combat = activeCombats.get(combatId);
            if (combat == null) {
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
                }
                return CombatActionResult.error("战斗不存在");
            }

            CombatCharacter character = combat.findCharacter(characterId);
            if (character == null || !character.isAlive()) {
                // 角色不存在或已死亡，检查战斗是否已结束
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
                }
                // 战斗未结束但角色已死亡
                CombatActionResult result = CombatActionResult.error("角色不存在或已死亡");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 检查当前回合（不推进行动条）
            Optional<String> currentTurn = combat.getCurrentTurnCharacterId();

            // 如果不是该玩家的回合，释放锁后阻塞等待
            if (currentTurn.isEmpty() || !currentTurn.get().equals(characterId)) {
                // 释放锁后调用阻塞等待
                lock.unlock();
                try {
                    return waitForTurn(combatId, characterId);
                } finally {
                    // 重新获取锁以便finally块正常释放
                    lock.lock();
                }
            }

            // 玩家行动（跳过回合），取消超时计时
            turnTimeoutManager.playerActed(combatId, characterId);

            // 跳过回合
            CombatActionResult result = skipTurnInternal(combat, characterId);

            if (result.isCombatEnded()) {
                return result;
            }

            // 通知所有等待的玩家，让他们重新检查是否轮到自己
            CombatTurnWaiter waiter = turnWaiters.get(combatId);
            if (waiter != null) {
                waiter.notifyAllWaiting();
            }

            // 处理后续回合直到轮到玩家或战斗结束
            return processUntilPlayerTurnOrEndInternal(combat, characterId, result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 等待战斗状态变化（阻塞等待）
     * 用于玩家在非自己回合时发送wait指令
     *
     * 阻塞返回条件：
     * 1. 有任何状态变化（其他玩家完成行动、超时空过等）
     * 2. 等待超时（10秒）
     * 3. 战斗结束
     *
     * 返回后前端根据最新状态决定下一步操作
     */
    public CombatActionResult waitForTurn(String combatId, String characterId) {
        // 首先检查战斗是否已结束
        Optional<CombatSettlementService.CombatEndInfo> endInfoOpt = settlementService.getEndedCombatInfo(combatId);
        if (endInfoOpt.isPresent()) {
            return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
        }

        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            endInfoOpt = settlementService.getEndedCombatInfo(combatId);
            if (endInfoOpt.isPresent()) {
                return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
            }
            return CombatActionResult.error("战斗不存在");
        }

        // 先检查当前状态
        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            combat = activeCombats.get(combatId);
            if (combat == null) {
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
                }
                return CombatActionResult.error("战斗不存在");
            }

            CombatCharacter character = combat.findCharacter(characterId);
            if (character == null || !character.isAlive()) {
                // 角色不存在或已死亡，检查战斗是否已结束
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
                }
                // 战斗未结束但角色已死亡
                CombatActionResult result = CombatActionResult.error("角色不存在或已死亡");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 检查战斗是否结束
            if (combat.isFinished()) {
                CombatActionResult result = CombatActionResult.success("战斗已结束");
                result.setCombatEnded(true);
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 检查当前回合，如果已经轮到自己则立即返回
            Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
            if (currentTurn.isPresent() && currentTurn.get().equals(characterId)) {
                CombatActionResult result = CombatActionResult.success("轮到你的回合");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }
        } finally {
            lock.unlock();
        }

        // 不是自己的回合，阻塞等待状态变化
        CombatTurnWaiter waiter = turnWaiters.get(combatId);
        if (waiter != null) {
            log.debug("[战斗 {}] 玩家 {} 开始阻塞等待状态变化", combatId, characterId);
            CombatTurnWaiter.WaitResult waitResult = waiter.waitForNextTurn(characterId);
            log.debug("[战斗 {}] 玩家 {} 等待结束: {}", combatId, characterId, waitResult.getStatus());
        }

        // 被唤醒或超时，返回当前最新状态
        endInfoOpt = settlementService.getEndedCombatInfo(combatId);
        if (endInfoOpt.isPresent()) {
            return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
        }

        combat = activeCombats.get(combatId);
        if (combat == null) {
            endInfoOpt = settlementService.getEndedCombatInfo(combatId);
            if (endInfoOpt.isPresent()) {
                return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
            }
            return CombatActionResult.error("战斗不存在");
        }

        // 返回当前状态，让前端根据状态决定下一步
        lock.lock();
        try {
            combat = activeCombats.get(combatId);
            if (combat == null) {
                endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
                }
                return CombatActionResult.error("战斗不存在");
            }

            if (combat.isFinished()) {
                CombatActionResult result = CombatActionResult.success("战斗已结束");
                result.setCombatEnded(true);
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            Optional<String> currentTurn = combat.getCurrentTurnCharacterId();
            if (currentTurn.isPresent() && currentTurn.get().equals(characterId)) {
                CombatActionResult result = CombatActionResult.success("轮到你的回合");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            } else {
                // 仍然不是自己的回合，返回状态让前端继续wait
                CombatActionResult result = CombatActionResult.success("未轮到你的回合，请输入wait继续等待");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }
        } finally {
            lock.unlock();
        }
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

        List<String> endLogs = settlementService.finishCombat(combat, turnTimeoutManager, turnWaiters, createRemover(combat.getCombatId()));
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
     * 撤退（主动退出战斗）
     * 根据设计文档：
     * - 只有PVE战斗（有敌人参与）才能撤退
     * - PVP战斗（纯玩家之间）不能撤退
     * - 撤退保留当前生命、法力等，无法获得战利品
     * - 敌人如果没有其他交战对象，状态应当自动重置
     */
    public CombatActionResult forfeit(String combatId, String characterId) {
        CombatInstance combat = activeCombats.get(combatId);
        if (combat == null) {
            return CombatActionResult.error("战斗不存在");
        }

        ReentrantLock lock = getCombatLock(combatId);
        lock.lock();
        try {
            combat = activeCombats.get(combatId);
            if (combat == null) {
                return CombatActionResult.error("战斗不存在");
            }

            // 检查战斗类型，只有PVE战斗才能撤退
            if (combat.getCombatType() != CombatInstance.CombatType.PVE) {
                return CombatActionResult.error("PVP战斗中不能撤退");
            }

            CombatCharacter character = combat.findCharacter(characterId);
            if (character == null || !character.isAlive()) {
                // 角色不存在或已死亡，检查战斗是否已结束
                Optional<CombatSettlementService.CombatEndInfo> endInfoOpt = settlementService.getEndedCombatInfo(combatId);
                if (endInfoOpt.isPresent()) {
                    return settlementService.createCombatEndedResult(endInfoOpt.get(), characterId);
                }
                // 战斗未结束但角色已死亡
                CombatActionResult result = CombatActionResult.error("角色不存在或已死亡");
                result.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
                return result;
            }

            // 只有玩家可以撤退
            if (!character.isPlayer()) {
                return CombatActionResult.error("只有玩家可以撤退");
            }

            // 撤退：标记为已撤退，但不设置死亡，保留当前生命和法力
            character.setRetreated(true);
            combat.addLog(character.getName() + " 撤退了");

            // 从行动条中移除该角色
            combat.getActionBar().remove(characterId);

            CombatActionResult result = CombatActionResult.success("撤退成功");

            // 检查战斗是否结束
            List<String> endLogs = settlementService.finishCombatWithRetreat(combat, turnTimeoutManager, turnWaiters, createRemover(combatId));
            if (endLogs != null) {
                result.setCombatEnded(true);
                for (String log : endLogs) {
                    result.addLog(log);
                }
            } else {
                // 战斗没有结束，通知所有等待的玩家
                CombatTurnWaiter waiter = turnWaiters.get(combatId);
                if (waiter != null) {
                    waiter.notifyAllWaiting();
                }
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取并移除战利品分配结果
     * 由CombatService在战斗结束后调用，用于持久化战利品
     */
    public CombatInstance.RewardDistribution getAndRemoveRewardDistribution(String combatId) {
        return settlementService.getAndRemoveRewardDistribution(combatId);
    }

    /**
     * 获取当前回合开始时间（毫秒时间戳）
     * @param combatId 战斗ID
     * @return 回合开始时间，如果没有则返回0
     */
    public long getTurnStartTime(String combatId) {
        return turnTimeoutManager.getTurnStartTime(combatId);
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

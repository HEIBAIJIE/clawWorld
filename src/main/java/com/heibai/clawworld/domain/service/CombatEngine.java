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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 战斗引擎 - 管理所有活跃的战斗
 *
 * 核心职责：
 * 1. 管理所有活跃战斗实例
 * 2. 定时推进所有战斗的行动条
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

    // 敌人AI
    private final EnemyAI enemyAI = new SimpleEnemyAI();

    // 线程池，用于处理AI决策
    private final ExecutorService aiExecutor = Executors.newCachedThreadPool();

    // 配置数据管理器
    private final ConfigDataManager configDataManager;

    public CombatEngine(ConfigDataManager configDataManager) {
        this.configDataManager = configDataManager;
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
     * 2. 推进行动条
     * 3. 阻塞等待，直到又轮到该玩家或战斗结束
     * 4. 返回期间的所有战斗日志
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

        // 阻塞等待下一回合
        return waitForNextTurnOrEnd(combatId, casterId, result);
    }

    /**
     * 阻塞等待下一回合或战斗结束
     */
    private CombatActionResult waitForNextTurnOrEnd(String combatId, String characterId, CombatActionResult previousResult) {
        CombatTurnWaiter waiter = turnWaiters.get(combatId);
        if (waiter == null) {
            return previousResult;
        }

        // 阻塞等待
        CombatTurnWaiter.WaitResult waitResult = waiter.waitForNextTurn(characterId);

        CombatInstance combat = activeCombats.get(combatId);

        if (waitResult.isTimeout()) {
            // 超时10秒，根据设计文档：视为回合空过
            log.warn("玩家 {} 在战斗 {} 中超时未行动，回合空过", characterId, combatId);

            if (combat != null) {
                // 跳过回合
                CombatActionResult result = skipTurnInternal(combat, characterId);
                result.setMessage("超时未行动，回合空过");
                return result;
            }

            previousResult.setSuccess(true);
            previousResult.setMessage("超时未行动，回合空过");
            return previousResult;
        }

        // 被唤醒，检查战斗状态
        if (combat == null || combat.getStatus() != com.heibai.clawworld.domain.combat.Combat.CombatStatus.ONGOING) {
            // 战斗已结束
            previousResult.setCombatEnded(true);
            if (combat != null) {
                previousResult.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
            }
            return previousResult;
        }

        // 又轮到该角色，返回期间的战斗日志
        previousResult.setBattleLog(convertLogsToStrings(combat.getAllLogs()));
        previousResult.setMessage("轮到你的回合");
        return previousResult;
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
        if (checkAndFinishCombat(combat)) {
            result.setCombatEnded(true);
        }

        return result;
    }

    /**
     * 执行对自身的技能
     */
    private void executeSelfSkill(CombatInstance combat, CombatCharacter caster, Skill skill, CombatActionResult result) {
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
    private void executeAllySingleSkill(CombatInstance combat, CombatCharacter caster, String targetId, Skill skill, CombatActionResult result) {
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
    private void executeAllyAllSkill(CombatInstance combat, CombatCharacter caster, Skill skill, CombatActionResult result) {
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
    private void executeEnemySingleSkill(CombatInstance combat, CombatCharacter caster, String targetId, Skill skill, CombatActionResult result) {
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
    private void executeEnemyAllSkill(CombatInstance combat, CombatCharacter caster, Skill skill, CombatActionResult result) {
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
     * 跳过回合（带阻塞等待）
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

        // 阻塞等待下一回合
        return waitForNextTurnOrEnd(combatId, characterId, result);
    }

    /**
     * 跳过回合（内部方法，不阻塞）
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

        if (checkAndFinishCombat(combat)) {
            result.setCombatEnded(true);
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

        if (checkAndFinishCombat(combat)) {
            result.setCombatEnded(true);
        }

        return result;
    }

    /**
     * 定时推进所有战斗的行动条
     * 每100ms执行一次
     */
    @Scheduled(fixedRate = 100)
    public void advanceAllCombats() {
        for (CombatInstance combat : activeCombats.values()) {
            if (combat.getStatus() == com.heibai.clawworld.domain.combat.Combat.CombatStatus.ONGOING) {
                // 检查超时
                if (combat.isTimeout()) {
                    handleCombatTimeout(combat);
                    continue;
                }

                // 推进行动条
                combat.advanceActionBars();

                // 检查是否有角色准备好行动
                processReadyCharacters(combat);
            }
        }
    }

    /**
     * 处理准备好行动的角色
     * 注意：可能有多个角色同时准备好
     */
    private void processReadyCharacters(CombatInstance combat) {
        List<String> readyCharacterIds = combat.getReadyCharacterIds();

        if (readyCharacterIds.isEmpty()) {
            return;
        }

        // 处理所有准备好的角色
        for (String characterId : readyCharacterIds) {
            CombatCharacter character = combat.findCharacter(characterId);

            if (character == null || !character.isAlive()) {
                // 角色不存在或已死亡，跳过回合
                combat.addLog("角色 " + characterId + " 不存在或已死亡，跳过回合");
                combat.resetActionBar(characterId);
                continue;
            }

            // 记录回合开始
            combat.addLog("=== 轮到 " + character.getName() + " 的回合 ===");

            // 检查是否是敌人
            if (character.isEnemy()) {
                // 敌人自动执行AI决策
                aiExecutor.submit(() -> executeEnemyAI(combat, character));
            } else if (character.isPlayer()) {
                // 玩家回合，通知等待的玩家
                CombatTurnWaiter waiter = turnWaiters.get(combat.getCombatId());
                if (waiter != null) {
                    waiter.notifyTurn(characterId);
                }
            }
        }
    }

    /**
     * 执行敌人AI
     */
    private void executeEnemyAI(CombatInstance combat, CombatCharacter enemy) {
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
     * 处理战斗超时
     * 根据设计文档：
     * - 玩家vs玩家超时：不分胜负
     * - 玩家vs敌人超时：视同全部玩家死亡
     */
    private void handleCombatTimeout(CombatInstance combat) {
        combat.setStatus(com.heibai.clawworld.domain.combat.Combat.CombatStatus.TIMEOUT);
        combat.addLog("战斗超时！");

        if (combat.getCombatType() == CombatInstance.CombatType.PVE) {
            // PVE战斗超时，所有玩家死亡
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

            // 触发玩家死亡惩罚（在战斗结算时处理）
            // 根据设计文档：持久化仅发生在战斗结算之后
        } else if (combat.getCombatType() == CombatInstance.CombatType.PVP) {
            // PVP战斗超时，平局
            combat.addLog("PVP战斗超时，不分胜负");
        }

        // 通知所有等待的玩家
        CombatTurnWaiter waiter = turnWaiters.get(combat.getCombatId());
        if (waiter != null) {
            waiter.notifyAllWaiting();
        }

        activeCombats.remove(combat.getCombatId());
        turnWaiters.remove(combat.getCombatId());
        log.info("战斗超时: combatId={}, type={}", combat.getCombatId(), combat.getCombatType());
    }

    /**
     * 检查并结束战斗
     */
    private boolean checkAndFinishCombat(CombatInstance combat) {
        if (combat.isFinished()) {
            combat.setStatus(com.heibai.clawworld.domain.combat.Combat.CombatStatus.FINISHED);

            Optional<CombatParty> winner = combat.getWinner();
            if (winner.isPresent()) {
                combat.addLog("阵营 " + winner.get().getFactionId() + " 获得胜利！");
                handleCombatRewards(combat, winner.get());
            } else {
                combat.addLog("战斗平局！");
            }

            // 通知所有等待的玩家
            CombatTurnWaiter waiter = turnWaiters.get(combat.getCombatId());
            if (waiter != null) {
                waiter.notifyAll();
            }

            activeCombats.remove(combat.getCombatId());
            turnWaiters.remove(combat.getCombatId());
            log.info("战斗结束: combatId={}", combat.getCombatId());
            return true;
        }

        return false;
    }

    /**
     * 处理战斗奖励
     * 根据设计文档：战利品归属于对敌人造成最后攻击的队伍，组队中战胜敌人战利品由队长持有
     * 注意：这里只记录战利品信息，实际的持久化在战斗结算后由CombatService处理
     */
    private void handleCombatRewards(CombatInstance combat, CombatParty winner) {
        log.info("战斗 {} 的胜利方 {} 获得战利品", combat.getCombatId(), winner.getFactionId());
        // 战利品信息已经在CombatInstance中记录，这里不需要额外处理
        // 实际的物品分配和持久化将在战斗结算后由CombatService处理
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

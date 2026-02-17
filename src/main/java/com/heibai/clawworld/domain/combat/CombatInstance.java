package com.heibai.clawworld.domain.combat;

import com.heibai.clawworld.domain.combat.Combat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战斗实例 - 代表一场独立的战斗
 * 根据设计文档：
 * - 支持多方加入战斗
 * - CTB条件回合制
 * - 战斗最多持续10分钟
 * - 战利品归属于对敌人造成最后攻击的队伍
 */
@Slf4j
@Data
public class CombatInstance {
    private String combatId;
    private String mapId;
    private long startTime;
    private Combat.CombatStatus status;
    private CombatType combatType; // 战斗类型

    // 参战方列表（key: 阵营ID, value: 参战方）
    private Map<String, CombatParty> parties;

    // 行动条（key: 角色ID, value: 行动条进度）
    private Map<String, ActionBarEntry> actionBar;

    // 战斗日志（带序列号）
    private List<CombatLogEntry> combatLog;

    // 日志序列号计数器
    private int logSequence = 0;

    // 伤害统计（用于判定战利品归属）
    private Map<String, DamageRecord> damageRecords;

    // 战利品分配结果（战斗结束时填充）
    private RewardDistribution rewardDistribution;

    // 战斗超时时间（10分钟）
    private static final long COMBAT_TIMEOUT_MS = 10 * 60 * 1000;

    public CombatInstance(String combatId, String mapId) {
        this.combatId = combatId;
        this.mapId = mapId;
        this.startTime = System.currentTimeMillis();
        this.status = Combat.CombatStatus.ONGOING;
        this.combatType = CombatType.UNKNOWN; // 默认未知，需要在添加参战方时判断
        this.parties = new ConcurrentHashMap<>();
        this.actionBar = new ConcurrentHashMap<>();
        this.combatLog = Collections.synchronizedList(new ArrayList<>());
        this.damageRecords = new ConcurrentHashMap<>();
        this.logSequence = 0;
    }

    /**
     * 添加参战方
     */
    public void addParty(String factionId, CombatParty party) {
        parties.put(factionId, party);
        addLog("阵营 " + factionId + " 加入战斗！");

        // 初始化该阵营所有角色的行动条
        for (CombatCharacter character : party.getCharacters()) {
            ActionBarEntry entry = new ActionBarEntry(character.getCharacterId(), character.getSpeed());
            actionBar.put(character.getCharacterId(), entry);
        }

        // 判断战斗类型
        updateCombatType();
    }

    /**
     * 更新战斗类型
     */
    private void updateCombatType() {
        boolean hasPlayer = false;
        boolean hasEnemy = false;

        for (CombatParty party : parties.values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.isPlayer()) {
                    hasPlayer = true;
                } else if (character.isEnemy()) {
                    hasEnemy = true;
                }
            }
        }

        if (hasPlayer && hasEnemy) {
            combatType = CombatType.PVE;
        } else if (hasPlayer) {
            combatType = CombatType.PVP;
        } else {
            combatType = CombatType.UNKNOWN;
        }
    }

    /**
     * 战斗类型
     */
    public enum CombatType {
        PVP,     // 玩家vs玩家
        PVE,     // 玩家vs敌人
        UNKNOWN  // 未知
    }

    /**
     * 添加角色到现有阵营
     */
    public void addCharacterToParty(String factionId, CombatCharacter character) {
        CombatParty party = parties.get(factionId);
        if (party == null) {
            party = new CombatParty(factionId);
            parties.put(factionId, party);
        }

        party.addCharacter(character);
        ActionBarEntry entry = new ActionBarEntry(character.getCharacterId(), character.getSpeed());
        actionBar.put(character.getCharacterId(), entry);

        addLog(character.getName() + " 加入战斗！");
    }

    /**
     * 检查战斗是否超时
     */
    public boolean isTimeout() {
        return System.currentTimeMillis() - startTime > COMBAT_TIMEOUT_MS;
    }

    /**
     * 检查战斗是否结束
     * 根据设计文档：只剩一个阵营存活时战斗结束
     */
    public boolean isFinished() {
        long aliveFactionsCount = parties.values().stream()
            .filter(CombatParty::hasAliveCharacters)
            .count();
        return aliveFactionsCount <= 1;
    }

    /**
     * 获取胜利方
     */
    public Optional<CombatParty> getWinner() {
        return parties.values().stream()
            .filter(CombatParty::hasAliveCharacters)
            .findFirst();
    }

    /**
     * 获取当前应该行动的角色（通过计算确定）
     * CTB战斗系统：根据速度计算谁最先到达行动条满值
     */
    public Optional<String> getCurrentTurnCharacterId() {
        // 收集所有存活角色的行动条
        List<ActionBarEntry> aliveEntries = new ArrayList<>();
        for (ActionBarEntry entry : actionBar.values()) {
            CombatCharacter character = findCharacter(entry.getCharacterId());
            if (character != null && character.isAlive()) {
                aliveEntries.add(entry);
            }
        }

        if (aliveEntries.isEmpty()) {
            return Optional.empty();
        }

        // 找出最快到达行动的角色（时间最短）
        ActionBarEntry nextActor = null;
        double minTime = Double.MAX_VALUE;

        for (ActionBarEntry entry : aliveEntries) {
            double timeToReady = entry.getTimeToReady();
            if (timeToReady < minTime ||
                (timeToReady == minTime && nextActor != null && entry.getSpeed() > nextActor.getSpeed())) {
                minTime = timeToReady;
                nextActor = entry;
            }
        }

        if (nextActor == null) {
            return Optional.empty();
        }

        // 所有存活角色的进度条按该时间推进
        for (ActionBarEntry entry : aliveEntries) {
            entry.advanceByTime(minTime);
        }

        return Optional.of(nextActor.getCharacterId());
    }

    /**
     * 重置角色的行动条（回合结束）
     */
    public void resetActionBar(String characterId) {
        ActionBarEntry entry = actionBar.get(characterId);
        if (entry != null) {
            entry.reset();
        }

        // 回合结束时减少该角色的技能冷却
        CombatCharacter character = findCharacter(characterId);
        if (character != null) {
            character.decreaseAllCooldowns();
        }
    }

    /**
     * 查找角色
     */
    public CombatCharacter findCharacter(String characterId) {
        for (CombatParty party : parties.values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.getCharacterId().equals(characterId)) {
                    return character;
                }
            }
        }
        return null;
    }

    /**
     * 根据名称查找角色
     */
    public CombatCharacter findCharacterByName(String name) {
        for (CombatParty party : parties.values()) {
            for (CombatCharacter character : party.getCharacters()) {
                if (character.getName().equals(name)) {
                    return character;
                }
            }
        }
        return null;
    }

    /**
     * 记录伤害（用于判定战利品归属）
     */
    public void recordDamage(String attackerFactionId, String targetId, int damage) {
        DamageRecord record = damageRecords.computeIfAbsent(targetId, k -> new DamageRecord(targetId));
        record.addDamage(attackerFactionId, damage);
    }

    /**
     * 获取对某个目标造成最后攻击的阵营（或DOT击杀时的最早攻击者）
     */
    public String getLootOwnerFaction(String targetId, boolean isDotKill) {
        DamageRecord record = damageRecords.get(targetId);
        return record != null ? record.getLootOwnerFaction(isDotKill) : null;
    }

    /**
     * 添加战斗日志
     */
    public void addLog(String logMessage) {
        CombatLogEntry entry = new CombatLogEntry(++logSequence, System.currentTimeMillis(), logMessage);
        combatLog.add(entry);
        log.info("[战斗 {}] #{} {}", combatId, entry.getSequence(), logMessage);
    }

    /**
     * 获取从指定序列号之后的日志（增量获取）
     */
    public List<CombatLogEntry> getLogsSince(int lastSequence) {
        return combatLog.stream()
            .filter(entry -> entry.getSequence() > lastSequence)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有日志
     */
    public List<CombatLogEntry> getAllLogs() {
        return new ArrayList<>(combatLog);
    }

    /**
     * 战斗日志条目
     */
    @Data
    public static class CombatLogEntry {
        private final int sequence;      // 序列号
        private final long timestamp;    // 时间戳
        private final String message;    // 日志内容

        public CombatLogEntry(int sequence, long timestamp, String message) {
            this.sequence = sequence;
            this.timestamp = timestamp;
            this.message = message;
        }
    }

    /**
     * 获取所有存活的角色
     */
    public List<CombatCharacter> getAllAliveCharacters() {
        return parties.values().stream()
            .flatMap(party -> party.getCharacters().stream())
            .filter(CombatCharacter::isAlive)
            .collect(Collectors.toList());
    }

    /**
     * 获取指定阵营的所有存活角色
     */
    public List<CombatCharacter> getAliveCharactersInFaction(String factionId) {
        CombatParty party = parties.get(factionId);
        if (party == null) {
            return Collections.emptyList();
        }
        return party.getCharacters().stream()
            .filter(CombatCharacter::isAlive)
            .collect(Collectors.toList());
    }

    /**
     * 获取敌对阵营的所有存活角色
     */
    public List<CombatCharacter> getEnemyCharacters(String factionId) {
        return parties.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(factionId))
            .flatMap(entry -> entry.getValue().getCharacters().stream())
            .filter(CombatCharacter::isAlive)
            .collect(Collectors.toList());
    }

    /**
     * 行动条条目
     * CTB战斗系统：通过数学计算确定行动顺序，无需定时模拟
     */
    @Data
    public static class ActionBarEntry {
        private String characterId;
        private int progress;
        private int speed;

        public static final int ACTION_BAR_MAX = 10000;

        public ActionBarEntry(String characterId, int speed) {
            this.characterId = characterId;
            this.speed = Math.max(1, speed); // 确保速度至少为1，避免除零
            this.progress = 0;
        }

        /**
         * 计算到达行动所需的"时间单位"
         */
        public double getTimeToReady() {
            if (progress >= ACTION_BAR_MAX) {
                return 0;
            }
            return (double) (ACTION_BAR_MAX - progress) / speed;
        }

        /**
         * 检查是否已准备好行动
         */
        public boolean isReady() {
            return progress >= ACTION_BAR_MAX;
        }

        /**
         * 增加进度（根据经过的时间单位）
         */
        public void advanceByTime(double timeUnits) {
            progress += (int) (timeUnits * speed);
        }

        /**
         * 行动后重置进度条
         */
        public void reset() {
            progress -= ACTION_BAR_MAX;
            if (progress < 0) {
                progress = 0;
            }
        }
    }

    /**
     * 伤害记录（用于判定战利品归属）
     */
    @Data
    public static class DamageRecord {
        private String targetId;
        private Map<String, Integer> damageByFaction;
        private String lastAttackerFaction;
        private String firstAttackerFaction; // 最早加入战斗的队伍

        public DamageRecord(String targetId) {
            this.targetId = targetId;
            this.damageByFaction = new HashMap<>();
        }

        public void addDamage(String factionId, int damage) {
            damageByFaction.merge(factionId, damage, Integer::sum);
            lastAttackerFaction = factionId;

            // 记录第一个攻击者
            if (firstAttackerFaction == null) {
                firstAttackerFaction = factionId;
            }
        }

        /**
         * 获取战利品归属阵营
         * 根据设计文档：
         * - 战利品归属于对敌人造成最后攻击的队伍
         * - 如果是流血等状态导致敌人死亡，则战利品归于最早加入战斗的队伍
         */
        public String getLootOwnerFaction(boolean isDotKill) {
            if (isDotKill) {
                return firstAttackerFaction;
            }
            return lastAttackerFaction;
        }
    }

    /**
     * 战利品分配结果
     * 根据设计文档：
     * - 每个玩家都获得全部经验
     * - 金钱平分
     * - 物品归队长
     */
    @Data
    public static class RewardDistribution {
        private String winnerFactionId;           // 胜利阵营ID
        private int totalExperience;              // 总经验（每人都获得这么多）
        private int totalGold;                    // 总金钱
        private int goldPerPlayer;                // 每人分得的金钱
        private List<String> items;               // 物品ID列表
        private String leaderId;                  // 队长ID（物品归属）
        private List<String> playerIds;           // 所有获得奖励的玩家ID
        private Map<String, PlayerFinalState> playerFinalStates; // 玩家战斗结束时的状态
        private List<DefeatedEnemy> defeatedEnemies; // 被击败的敌人列表

        public RewardDistribution() {
            this.items = new ArrayList<>();
            this.playerIds = new ArrayList<>();
            this.playerFinalStates = new HashMap<>();
            this.defeatedEnemies = new ArrayList<>();
        }
    }

    /**
     * 玩家战斗结束时的状态
     */
    @Data
    public static class PlayerFinalState {
        private int currentHealth;
        private int currentMana;

        public PlayerFinalState(int currentHealth, int currentMana) {
            this.currentHealth = currentHealth;
            this.currentMana = currentMana;
        }
    }

    /**
     * 被击败的敌人信息
     */
    @Data
    public static class DefeatedEnemy {
        private String mapId;
        private String instanceId;
        private int respawnSeconds;

        public DefeatedEnemy(String mapId, String instanceId, int respawnSeconds) {
            this.mapId = mapId;
            this.instanceId = instanceId;
            this.respawnSeconds = respawnSeconds;
        }
    }
}

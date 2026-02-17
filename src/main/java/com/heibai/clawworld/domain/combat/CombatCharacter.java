package com.heibai.clawworld.domain.combat;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗角色 - 战斗中的角色数据
 * 包含战斗属性和状态
 */
@Data
public class CombatCharacter {
    // 基础信息
    private String characterId;
    private String characterType; // PLAYER, ENEMY, NPC
    private String name;
    private String factionId;
    private String partyId; // 队伍ID（用于战利品分配）
    private String enemyConfigId; // 敌人配置ID（仅敌人使用，用于获取掉落配置）
    private boolean partyLeader; // 是否是队长（用于战利品分配）

    // 敌人实例信息（仅敌人使用，用于战斗结束后更新状态）
    private String enemyMapId; // 敌人所在地图ID
    private String enemyInstanceId; // 敌人实例ID
    private int enemyRespawnSeconds; // 敌人刷新时间（秒）

    // 生命和法力
    private int maxHealth;
    private int currentHealth;
    private int maxMana;
    private int currentMana;

    // 战斗属性
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;

    // 技能列表
    private List<String> skillIds;

    // 技能冷却（key: 技能ID, value: 剩余冷却回合数）
    private Map<String, Integer> skillCooldowns;

    // 状态
    private boolean isDead;

    public CombatCharacter() {
        this.skillIds = new ArrayList<>();
        this.skillCooldowns = new HashMap<>();
        this.isDead = false;
    }

    /**
     * 检查是否存活
     */
    public boolean isAlive() {
        return !isDead && currentHealth > 0;
    }

    /**
     * 受到伤害
     */
    public void takeDamage(int damage) {
        currentHealth = Math.max(0, currentHealth - damage);
        if (currentHealth == 0) {
            isDead = true;
        }
    }

    /**
     * 恢复生命
     */
    public void heal(int amount) {
        if (!isDead) {
            currentHealth = Math.min(maxHealth, currentHealth + amount);
        }
    }

    /**
     * 消耗法力
     */
    public boolean consumeMana(int amount) {
        if (currentMana >= amount) {
            currentMana -= amount;
            return true;
        }
        return false;
    }

    /**
     * 恢复法力
     */
    public void restoreMana(int amount) {
        currentMana = Math.min(maxMana, currentMana + amount);
    }

    /**
     * 检查技能是否在冷却中
     */
    public boolean isSkillOnCooldown(String skillId) {
        Integer cooldown = skillCooldowns.get(skillId);
        return cooldown != null && cooldown > 0;
    }

    /**
     * 设置技能冷却
     */
    public void setSkillCooldown(String skillId, int turns) {
        skillCooldowns.put(skillId, turns);
    }

    /**
     * 减少所有技能冷却
     */
    public void decreaseAllCooldowns() {
        skillCooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
    }

    /**
     * 是否是玩家
     */
    public boolean isPlayer() {
        return "PLAYER".equals(characterType);
    }

    /**
     * 是否是敌人
     */
    public boolean isEnemy() {
        return "ENEMY".equals(characterType);
    }

    /**
     * 是否是队长
     */
    public boolean isPartyLeader() {
        return partyLeader;
    }
}

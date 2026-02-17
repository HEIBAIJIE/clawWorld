package com.heibai.clawworld.infrastructure.persistence.mapper;

import com.heibai.clawworld.domain.character.Character;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.infrastructure.config.data.character.EnemyConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗相关对象的映射器
 * 处理领域对象和持久化实体到战斗角色的转换
 */
@Component
public class CombatMapper {

    /**
     * 将领域角色转换为战斗角色
     */
    public CombatCharacter toCombatCharacter(Character character) {
        if (character == null) {
            return null;
        }

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

        // 设置队长标记（用于战利品分配）
        if (character instanceof Player) {
            Player player = (Player) character;
            combatChar.setPartyLeader(player.isPartyLeader());
            combatChar.setPartyId(player.getPartyId());
        }

        return combatChar;
    }

    /**
     * 将敌人实例和配置转换为战斗角色
     */
    public CombatCharacter toCombatCharacter(EnemyInstanceEntity enemy, EnemyConfig config) {
        if (enemy == null || config == null) {
            return null;
        }

        CombatCharacter combatChar = new CombatCharacter();
        combatChar.setCharacterId(enemy.getId());
        combatChar.setCharacterType("ENEMY");
        combatChar.setName(enemy.getDisplayName());
        combatChar.setFactionId("enemy_" + enemy.getTemplateId());
        combatChar.setMaxHealth(config.getHealth());
        combatChar.setCurrentHealth(enemy.getCurrentHealth());
        combatChar.setMaxMana(config.getMana());
        combatChar.setCurrentMana(enemy.getCurrentMana());
        combatChar.setPhysicalAttack(config.getPhysicalAttack());
        combatChar.setPhysicalDefense(config.getPhysicalDefense());
        combatChar.setMagicAttack(config.getMagicAttack());
        combatChar.setMagicDefense(config.getMagicDefense());
        combatChar.setSpeed(config.getSpeed());
        combatChar.setCritRate(config.getCritRate());
        combatChar.setCritDamage(config.getCritDamage());
        combatChar.setHitRate(config.getHitRate());
        combatChar.setDodgeRate(config.getDodgeRate());

        // 设置敌人配置ID（用于战利品计算）
        combatChar.setEnemyConfigId(enemy.getTemplateId());

        // 设置敌人实例信息（用于战斗结束后更新状态）
        combatChar.setEnemyMapId(enemy.getMapId());
        combatChar.setEnemyInstanceId(enemy.getInstanceId());
        combatChar.setEnemyRespawnSeconds(config.getRespawnSeconds());

        // 解析敌人技能
        List<String> skillIds = new ArrayList<>();
        if (config.getSkills() != null && !config.getSkills().isEmpty()) {
            String[] skills = config.getSkills().split(",");
            for (String skill : skills) {
                skillIds.add(skill.trim());
            }
        }
        combatChar.setSkillIds(skillIds);

        return combatChar;
    }
}

package com.heibai.clawworld.infrastructure.config.loader;

import com.heibai.clawworld.infrastructure.config.data.character.*;
import com.heibai.clawworld.infrastructure.util.CsvReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色配置加载器
 */
@Component
@RequiredArgsConstructor
public class CharacterConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(CharacterConfigLoader.class);
    private final CsvReader csvReader;
    private final ResourceLoader resourceLoader;

    private final Map<String, EnemyConfig> enemyConfigs = new ConcurrentHashMap<>();
    private final Map<String, NpcConfig> npcConfigs = new ConcurrentHashMap<>();
    private final Map<String, RoleConfig> roleConfigs = new ConcurrentHashMap<>();
    private final List<EnemyLootConfig> enemyLootConfigs = new ArrayList<>();
    private final List<NpcShopItemConfig> npcShopItemConfigs = new ArrayList<>();
    private final List<RoleSkillConfig> roleSkillConfigs = new ArrayList<>();

    public void loadEnemies() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/enemies.csv");
            if (!resource.exists()) {
                log.warn("enemies.csv not found, skipping");
                return;
            }

            List<EnemyConfig> enemies = csvReader.readCsv(resource.getInputStream(), record -> {
                EnemyConfig enemy = new EnemyConfig();
                enemy.setId(csvReader.getString(record, "id"));
                enemy.setName(csvReader.getString(record, "name"));
                enemy.setDescription(csvReader.getString(record, "description"));
                enemy.setLevel(csvReader.getInt(record, "level"));
                enemy.setTier(csvReader.getString(record, "tier"));
                enemy.setHealth(csvReader.getInt(record, "health"));
                enemy.setMana(csvReader.getInt(record, "mana"));
                enemy.setPhysicalAttack(csvReader.getInt(record, "physicalAttack"));
                enemy.setPhysicalDefense(csvReader.getInt(record, "physicalDefense"));
                enemy.setMagicAttack(csvReader.getInt(record, "magicAttack"));
                enemy.setMagicDefense(csvReader.getInt(record, "magicDefense"));
                enemy.setSpeed(csvReader.getInt(record, "speed"));
                enemy.setCritRate(csvReader.getDouble(record, "critRate"));
                enemy.setCritDamage(csvReader.getDouble(record, "critDamage"));
                enemy.setHitRate(csvReader.getDouble(record, "hitRate"));
                enemy.setDodgeRate(csvReader.getDouble(record, "dodgeRate"));
                enemy.setSkills(csvReader.getString(record, "skills"));
                enemy.setExpMin(csvReader.getInt(record, "expMin"));
                enemy.setExpMax(csvReader.getInt(record, "expMax"));
                enemy.setGoldMin(csvReader.getInt(record, "goldMin"));
                enemy.setGoldMax(csvReader.getInt(record, "goldMax"));
                enemy.setRespawnSeconds(csvReader.getInt(record, "respawnSeconds"));
                enemy.setWalkSprite(csvReader.getStringOrNull(record, "walkSprite"));
                enemy.setPortrait(csvReader.getStringOrNull(record, "portrait"));
                return enemy;
            });

            enemyConfigs.clear();
            enemies.forEach(enemy -> enemyConfigs.put(enemy.getId(), enemy));
            log.info("Loaded {} enemies", enemies.size());
        } catch (IOException e) {
            log.error("Error loading enemies.csv", e);
        }
    }

    public void loadNpcs() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/npcs.csv");
            if (!resource.exists()) {
                log.warn("npcs.csv not found, skipping");
                return;
            }

            List<NpcConfig> npcs = csvReader.readCsv(resource.getInputStream(), record -> {
                NpcConfig npc = new NpcConfig();
                npc.setId(csvReader.getString(record, "id"));
                npc.setName(csvReader.getString(record, "name"));
                npc.setDescription(csvReader.getString(record, "description"));
                npc.setHasShop(csvReader.getBoolean(record, "hasShop"));
                npc.setHasDialogue(csvReader.getBoolean(record, "hasDialogue"));
                npc.setDialogues(csvReader.getString(record, "dialogues"));
                npc.setShopGold(csvReader.getInt(record, "shopGold"));
                npc.setShopRefreshSeconds(csvReader.getInt(record, "shopRefreshSeconds"));
                npc.setPriceMultiplier(csvReader.getDouble(record, "priceMultiplier"));
                npc.setWalkSprite(csvReader.getStringOrNull(record, "walkSprite"));
                npc.setPortrait(csvReader.getStringOrNull(record, "portrait"));
                return npc;
            });

            npcConfigs.clear();
            npcs.forEach(npc -> npcConfigs.put(npc.getId(), npc));
            log.info("Loaded {} NPCs", npcs.size());
        } catch (IOException e) {
            log.error("Error loading npcs.csv", e);
        }
    }

    public void loadRoles() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/roles.csv");
            if (!resource.exists()) {
                log.warn("roles.csv not found, skipping");
                return;
            }

            List<RoleConfig> roles = csvReader.readCsv(resource.getInputStream(), record -> {
                RoleConfig role = new RoleConfig();
                role.setId(csvReader.getString(record, "id"));
                role.setName(csvReader.getString(record, "name"));
                role.setDescription(csvReader.getString(record, "description"));
                role.setBaseHealth(csvReader.getInt(record, "baseHealth"));
                role.setBaseMana(csvReader.getInt(record, "baseMana"));
                role.setBasePhysicalAttack(csvReader.getInt(record, "basePhysicalAttack"));
                role.setBasePhysicalDefense(csvReader.getInt(record, "basePhysicalDefense"));
                role.setBaseMagicAttack(csvReader.getInt(record, "baseMagicAttack"));
                role.setBaseMagicDefense(csvReader.getInt(record, "baseMagicDefense"));
                role.setBaseSpeed(csvReader.getInt(record, "baseSpeed"));
                role.setBaseCritRate(csvReader.getDouble(record, "baseCritRate"));
                role.setBaseCritDamage(csvReader.getDouble(record, "baseCritDamage"));
                role.setBaseHitRate(csvReader.getDouble(record, "baseHitRate"));
                role.setBaseDodgeRate(csvReader.getDouble(record, "baseDodgeRate"));
                role.setHealthPerLevel(csvReader.getDouble(record, "healthPerLevel"));
                role.setManaPerLevel(csvReader.getDouble(record, "manaPerLevel"));
                role.setPhysicalAttackPerLevel(csvReader.getDouble(record, "physicalAttackPerLevel"));
                role.setPhysicalDefensePerLevel(csvReader.getDouble(record, "physicalDefensePerLevel"));
                role.setMagicAttackPerLevel(csvReader.getDouble(record, "magicAttackPerLevel"));
                role.setMagicDefensePerLevel(csvReader.getDouble(record, "magicDefensePerLevel"));
                role.setSpeedPerLevel(csvReader.getDouble(record, "speedPerLevel"));
                role.setCritRatePerLevel(csvReader.getDouble(record, "critRatePerLevel"));
                role.setCritDamagePerLevel(csvReader.getDouble(record, "critDamagePerLevel"));
                role.setHitRatePerLevel(csvReader.getDouble(record, "hitRatePerLevel"));
                role.setDodgeRatePerLevel(csvReader.getDouble(record, "dodgeRatePerLevel"));
                role.setWalkSprite(csvReader.getStringOrNull(record, "walkSprite"));
                role.setPortrait(csvReader.getStringOrNull(record, "portrait"));
                return role;
            });

            roleConfigs.clear();
            roles.forEach(role -> roleConfigs.put(role.getId(), role));
            log.info("Loaded {} roles", roles.size());
        } catch (IOException e) {
            log.error("Error loading roles.csv", e);
        }
    }

    public void loadEnemyLoot() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/enemy_loot.csv");
            if (!resource.exists()) {
                log.warn("enemy_loot.csv not found, skipping");
                return;
            }

            List<EnemyLootConfig> loots = csvReader.readCsv(resource.getInputStream(), record -> {
                EnemyLootConfig loot = new EnemyLootConfig();
                loot.setEnemyId(csvReader.getString(record, "enemyId"));
                loot.setItemId(csvReader.getString(record, "itemId"));
                loot.setRarity(csvReader.getString(record, "rarity"));
                loot.setDropRate(csvReader.getDouble(record, "dropRate"));
                return loot;
            });

            enemyLootConfigs.clear();
            enemyLootConfigs.addAll(loots);
            log.info("Loaded {} enemy loot entries", loots.size());
        } catch (IOException e) {
            log.error("Error loading enemy_loot.csv", e);
        }
    }

    public void loadNpcShopItems() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/npc_shop_items.csv");
            if (!resource.exists()) {
                log.warn("npc_shop_items.csv not found, skipping");
                return;
            }

            List<NpcShopItemConfig> shopItems = csvReader.readCsv(resource.getInputStream(), record -> {
                NpcShopItemConfig item = new NpcShopItemConfig();
                item.setNpcId(csvReader.getString(record, "npcId"));
                item.setItemId(csvReader.getString(record, "itemId"));
                item.setQuantity(csvReader.getInt(record, "quantity"));
                return item;
            });

            npcShopItemConfigs.clear();
            npcShopItemConfigs.addAll(shopItems);
            log.info("Loaded {} NPC shop item entries", shopItems.size());
        } catch (IOException e) {
            log.error("Error loading npc_shop_items.csv", e);
        }
    }

    public void loadRoleSkills() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/role_skills.csv");
            if (!resource.exists()) {
                log.warn("role_skills.csv not found, skipping");
                return;
            }

            List<RoleSkillConfig> roleSkills = csvReader.readCsv(resource.getInputStream(), record -> {
                RoleSkillConfig rs = new RoleSkillConfig();
                rs.setRoleId(csvReader.getString(record, "roleId"));
                rs.setSkillId(csvReader.getString(record, "skillId"));
                rs.setLearnLevel(csvReader.getInt(record, "learnLevel"));
                return rs;
            });

            roleSkillConfigs.clear();
            roleSkillConfigs.addAll(roleSkills);
            log.info("Loaded {} role skill entries", roleSkills.size());
        } catch (IOException e) {
            log.error("Error loading role_skills.csv", e);
        }
    }

    public EnemyConfig getEnemy(String id) {
        return enemyConfigs.get(id);
    }

    public NpcConfig getNpc(String id) {
        return npcConfigs.get(id);
    }

    public RoleConfig getRole(String id) {
        return roleConfigs.get(id);
    }

    public Map<String, EnemyConfig> getAllEnemies() {
        return enemyConfigs;
    }

    public Map<String, NpcConfig> getAllNpcs() {
        return npcConfigs;
    }

    public Map<String, RoleConfig> getAllRoles() {
        return roleConfigs;
    }

    public List<EnemyLootConfig> getEnemyLoot(String enemyId) {
        return enemyLootConfigs.stream()
                .filter(loot -> loot.getEnemyId().equals(enemyId))
                .toList();
    }

    public List<NpcShopItemConfig> getNpcShopItems(String npcId) {
        return npcShopItemConfigs.stream()
                .filter(item -> item.getNpcId().equals(npcId))
                .toList();
    }

    public List<RoleSkillConfig> getRoleSkills(String roleId) {
        return roleSkillConfigs.stream()
                .filter(rs -> rs.getRoleId().equals(roleId))
                .toList();
    }
}

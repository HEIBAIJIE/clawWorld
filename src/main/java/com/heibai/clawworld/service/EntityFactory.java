package com.heibai.clawworld.service;

import com.heibai.clawworld.config.character.EnemyConfig;
import com.heibai.clawworld.config.character.EnemyLootConfig;
import com.heibai.clawworld.config.character.NpcConfig;
import com.heibai.clawworld.config.map.MapEntityConfig;
import com.heibai.clawworld.domain.character.Enemy;
import com.heibai.clawworld.domain.character.Npc;
import com.heibai.clawworld.domain.item.Rarity;
import com.heibai.clawworld.domain.map.Waypoint;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实体工厂 - 负责从配置模板创建具体的地图实体实例
 */
@Service
@RequiredArgsConstructor
public class EntityFactory {

    private static final Logger log = LoggerFactory.getLogger(EntityFactory.class);
    private final ConfigDataManager configDataManager;

    /**
     * 从MapEntityConfig创建Enemy实例
     * @param config 地图实体配置
     * @param mapId 地图ID
     * @return Enemy实例，如果创建失败返回null
     */
    public Enemy createEnemyInstance(MapEntityConfig config, String mapId) {
        EnemyConfig template = configDataManager.getEnemy(config.getEntityId());
        if (template == null) {
            log.error("Enemy template not found: {}", config.getEntityId());
            return null;
        }

        Enemy enemy = new Enemy();

        // 设置实例ID和名称
        String instanceId = config.getInstanceId();
        if (instanceId == null || instanceId.isEmpty()) {
            // 如果没有指定instanceId，使用模板ID作为实例ID
            instanceId = config.getEntityId();
        }

        enemy.setId(instanceId);

        // 根据instanceId生成显示名称
        // 例如: goblin_1 -> "哥布林#1", goblin_2 -> "哥布林#2"
        String displayName = generateDisplayName(template.getName(), instanceId, config.getEntityId());
        enemy.setName(displayName);

        // 从模板复制基础属性
        enemy.setDescription(template.getDescription());
        enemy.setLevel(template.getLevel());
        enemy.setTier(Enemy.EnemyTier.valueOf(template.getTier()));

        // 战斗属性
        enemy.setMaxHealth(template.getHealth());
        enemy.setCurrentHealth(template.getHealth());
        enemy.setMaxMana(template.getMana());
        enemy.setCurrentMana(template.getMana());
        enemy.setPhysicalAttack(template.getPhysicalAttack());
        enemy.setPhysicalDefense(template.getPhysicalDefense());
        enemy.setMagicAttack(template.getMagicAttack());
        enemy.setMagicDefense(template.getMagicDefense());
        enemy.setSpeed(template.getSpeed());
        enemy.setCritRate(template.getCritRate());
        enemy.setCritDamage(template.getCritDamage());
        enemy.setHitRate(template.getHitRate());
        enemy.setDodgeRate(template.getDodgeRate());

        // 技能列表
        if (template.getSkills() != null && !template.getSkills().isEmpty()) {
            List<String> skills = Arrays.stream(template.getSkills().split(";"))
                    .map(String::trim)
                    .collect(Collectors.toList());
            enemy.setSkills(skills);
        }

        // 掉落配置
        enemy.setExpMin(template.getExpMin());
        enemy.setExpMax(template.getExpMax());
        enemy.setGoldMin(template.getGoldMin());
        enemy.setGoldMax(template.getGoldMax());

        // 加载掉落表
        List<EnemyLootConfig> lootConfigs = configDataManager.getEnemyLoot(config.getEntityId());
        List<Enemy.LootDrop> lootTable = lootConfigs.stream()
                .map(lc -> {
                    Enemy.LootDrop drop = new Enemy.LootDrop();
                    drop.setItemId(lc.getItemId());
                    drop.setRarity(Rarity.valueOf(lc.getRarity()));
                    drop.setDropRate(lc.getDropRate());
                    return drop;
                })
                .collect(Collectors.toList());
        enemy.setLootTable(lootTable);

        // 刷新配置
        enemy.setRespawnSeconds(template.getRespawnSeconds());
        enemy.setLastDeathTime(null);

        // 位置
        enemy.setX(config.getX());
        enemy.setY(config.getY());

        // 阵营 - 敌人默认为敌对阵营
        enemy.setFaction("ENEMY");

        log.debug("Created enemy instance: {} at ({}, {}) on map {}",
                enemy.getName(), enemy.getX(), enemy.getY(), mapId);

        return enemy;
    }

    /**
     * 从MapEntityConfig创建NPC实例
     */
    public Npc createNpcInstance(MapEntityConfig config, String mapId) {
        NpcConfig template = configDataManager.getNpc(config.getEntityId());
        if (template == null) {
            log.error("NPC template not found: {}", config.getEntityId());
            return null;
        }

        Npc npc = new Npc();
        npc.setId(config.getEntityId());
        npc.setName(template.getName());
        npc.setDescription(template.getDescription());
        npc.setX(config.getX());
        npc.setY(config.getY());
        npc.setHasShop(template.isHasShop());
        npc.setHasDialogue(template.isHasDialogue());

        if (template.getDialogues() != null && !template.getDialogues().isEmpty()) {
            List<String> dialogues = Arrays.stream(template.getDialogues().split(";"))
                    .map(String::trim)
                    .collect(Collectors.toList());
            npc.setDialogues(dialogues);
        }

        npc.setShopGold(template.getShopGold());
        npc.setShopRefreshSeconds(template.getShopRefreshSeconds());
        npc.setPriceMultiplier(template.getPriceMultiplier());
        npc.setFaction("FRIENDLY");

        log.debug("Created NPC instance: {} at ({}, {}) on map {}",
                npc.getName(), npc.getX(), npc.getY(), mapId);

        return npc;
    }

    /**
     * 从MapEntityConfig创建Waypoint实例
     */
    public Waypoint createWaypointInstance(MapEntityConfig config, String mapId) {
        var template = configDataManager.getWaypoint(config.getEntityId());
        if (template == null) {
            log.error("Waypoint template not found: {}", config.getEntityId());
            return null;
        }

        Waypoint waypoint = new Waypoint();
        waypoint.setId(config.getEntityId());
        waypoint.setName(template.getName());
        waypoint.setDescription(template.getDescription());
        waypoint.setX(config.getX());
        waypoint.setY(config.getY());
        waypoint.setTargetMapId(template.getTargetMapId());
        waypoint.setTargetX(template.getTargetX());
        waypoint.setTargetY(template.getTargetY());

        log.debug("Created Waypoint instance: {} at ({}, {}) on map {}",
                waypoint.getName(), waypoint.getX(), waypoint.getY(), mapId);

        return waypoint;
    }

    /**
     * 生成显示名称
     * 例如: templateName="哥布林", instanceId="goblin_1", templateId="goblin" -> "哥布林#1"
     */
    private String generateDisplayName(String templateName, String instanceId, String templateId) {
        // 如果instanceId就是templateId，说明没有编号，直接返回模板名称
        if (instanceId.equals(templateId)) {
            return templateName;
        }

        // 尝试从instanceId中提取编号
        // 假设格式为: templateId_number 或 templateId_suffix
        String suffix = instanceId.substring(templateId.length());
        if (suffix.startsWith("_")) {
            suffix = suffix.substring(1);
            // 尝试解析为数字
            try {
                int number = Integer.parseInt(suffix);
                return templateName + "#" + number;
            } catch (NumberFormatException e) {
                // 如果不是数字，直接拼接
                return templateName + "#" + suffix;
            }
        }

        // 如果格式不符合预期，返回原始名称
        return templateName;
    }
}

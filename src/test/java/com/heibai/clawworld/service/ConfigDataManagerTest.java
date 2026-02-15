package com.heibai.clawworld.service;

import com.heibai.clawworld.model.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConfigDataManagerTest {

    private static final Logger log = LoggerFactory.getLogger(ConfigDataManagerTest.class);

    @Autowired
    private ConfigDataManager configDataManager;

    @Test
    void testConfigDataLoaded() {
        log.info("=== 配置数据加载验证 ===");
        log.info("物品数量: {}", configDataManager.getAllItems().size());
        log.info("装备数量: {}", configDataManager.getAllEquipment().size());
        log.info("技能数量: {}", configDataManager.getAllSkills().size());
        log.info("敌人数量: {}", configDataManager.getAllEnemies().size());
        log.info("NPC数量: {}", configDataManager.getAllNpcs().size());
        log.info("地图数量: {}", configDataManager.getAllMaps().size());

        assertTrue(configDataManager.getAllItems().size() > 0, "应该加载了物品数据");
        assertTrue(configDataManager.getAllEquipment().size() > 0, "应该加载了装备数据");
        assertTrue(configDataManager.getAllSkills().size() > 0, "应该加载了技能数据");
        assertTrue(configDataManager.getAllEnemies().size() > 0, "应该加载了敌人数据");
        assertTrue(configDataManager.getAllNpcs().size() > 0, "应该加载了NPC数据");
        assertTrue(configDataManager.getAllMaps().size() > 0, "应该加载了地图数据");

        log.info("\n=== 示例数据 ===");
        configDataManager.getAllItems().stream().limit(3).forEach(item ->
            log.info("物品: {} - {}", item.getName(), item.getDescription())
        );

        configDataManager.getAllSkills().stream().limit(3).forEach(skill ->
            log.info("技能: {} - {} (伤害倍率: {})", skill.getName(), skill.getDescription(), skill.getDamageMultiplier())
        );

        configDataManager.getAllEnemies().stream().limit(3).forEach(enemy ->
            log.info("敌人: {} Lv.{} - {} (生命: {}, 攻击: {})",
                enemy.getName(), enemy.getLevel(), enemy.getTier(), enemy.getHealth(), enemy.getPhysicalAttack())
        );

        log.info("=== 配置加载完成 ===");
    }
}

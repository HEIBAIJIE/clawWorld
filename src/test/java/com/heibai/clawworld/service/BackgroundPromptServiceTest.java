package com.heibai.clawworld.service;

import com.heibai.clawworld.config.character.RoleConfig;
import com.heibai.clawworld.config.map.MapConfig;
import com.heibai.clawworld.config.map.WaypointConfig;
import com.heibai.clawworld.config.skill.SkillConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BackgroundPromptService单元测试
 */
@ExtendWith(MockitoExtension.class)
class BackgroundPromptServiceTest {

    @Mock
    private ConfigDataManager configDataManager;

    @InjectMocks
    private BackgroundPromptService backgroundPromptService;

    private List<MapConfig> testMaps;
    private List<WaypointConfig> testWaypoints;
    private List<RoleConfig> testRoles;

    @BeforeEach
    void setUp() {
        // 准备测试地图
        MapConfig map1 = new MapConfig();
        map1.setId("map1");
        map1.setName("新手村");
        map1.setDescription("安全的起始地点");
        map1.setWidth(10);
        map1.setHeight(10);
        map1.setSafe(true);

        MapConfig map2 = new MapConfig();
        map2.setId("map2");
        map2.setName("哥布林森林");
        map2.setDescription("充满哥布林的危险森林");
        map2.setWidth(15);
        map2.setHeight(15);
        map2.setSafe(false);
        map2.setRecommendedLevel(5);

        testMaps = Arrays.asList(map1, map2);

        // 准备测试传送点
        WaypointConfig wp1 = new WaypointConfig();
        wp1.setId("wp1");
        wp1.setMapId("map1");
        wp1.setName("村口传送点");
        wp1.setX(5);
        wp1.setY(5);
        wp1.setConnectedWaypointIds(Arrays.asList("wp2"));

        WaypointConfig wp2 = new WaypointConfig();
        wp2.setId("wp2");
        wp2.setMapId("map2");
        wp2.setName("森林入口");
        wp2.setX(0);
        wp2.setY(0);
        wp2.setConnectedWaypointIds(Arrays.asList("wp1"));

        testWaypoints = Arrays.asList(wp1, wp2);

        // 准备测试职业
        RoleConfig warrior = new RoleConfig();
        warrior.setId("warrior");
        warrior.setName("战士");
        warrior.setDescription("近战物理职业");
        warrior.setBaseHealth(150);
        warrior.setBaseMana(50);
        warrior.setBasePhysicalAttack(20);
        warrior.setBasePhysicalDefense(15);
        warrior.setBaseMagicAttack(5);
        warrior.setBaseMagicDefense(10);
        warrior.setBaseSpeed(100);

        testRoles = Arrays.asList(warrior);
    }

    @Test
    void testGenerateBackgroundPrompt_NewPlayer_ShouldContainAllSections() {
        // Arrange
        when(configDataManager.getAllMaps()).thenReturn(testMaps);
        when(configDataManager.getAllWaypoints()).thenReturn(testWaypoints);
        when(configDataManager.getAllRoles()).thenReturn(testRoles);
        when(configDataManager.getMap("map1")).thenReturn(testMaps.get(0));
        when(configDataManager.getMap("map2")).thenReturn(testMaps.get(1));

        // Act
        String prompt = backgroundPromptService.generateBackgroundPrompt(null);

        // Assert
        assertNotNull(prompt);
        assertTrue(prompt.contains("ClawWorld 游戏概述"));
        assertTrue(prompt.contains("游戏目标"));
        assertTrue(prompt.contains("指令手册"));
        assertTrue(prompt.contains("地图信息"));
        assertTrue(prompt.contains("新手村"));
        assertTrue(prompt.contains("哥布林森林"));
        assertTrue(prompt.contains("新玩家"));
        assertTrue(prompt.contains("可选职业"));
        assertTrue(prompt.contains("战士"));
        assertTrue(prompt.contains("register"));

        verify(configDataManager).getAllMaps();
        verify(configDataManager).getAllWaypoints();
        verify(configDataManager).getAllRoles();
    }

    @Test
    void testGenerateBackgroundPrompt_ExistingPlayer_ShouldContainPlayerInfo() {
        // Arrange
        Player player = createTestPlayer();

        RoleConfig warrior = testRoles.get(0);
        SkillConfig skill = new SkillConfig();
        skill.setId("skill1");
        skill.setName("重击");
        skill.setDescription("造成150%物理伤害");

        when(configDataManager.getAllMaps()).thenReturn(testMaps);
        when(configDataManager.getAllWaypoints()).thenReturn(testWaypoints);
        when(configDataManager.getMap("map1")).thenReturn(testMaps.get(0));
        when(configDataManager.getMap("map2")).thenReturn(testMaps.get(1));
        when(configDataManager.getRole("warrior")).thenReturn(warrior);
        when(configDataManager.getSkill("skill1")).thenReturn(skill);

        // Act
        String prompt = backgroundPromptService.generateBackgroundPrompt(player);

        // Assert
        assertNotNull(prompt);
        assertTrue(prompt.contains("你的角色信息"));
        assertTrue(prompt.contains("测试玩家"));
        assertTrue(prompt.contains("战士"));
        assertTrue(prompt.contains("等级：5"));
        assertTrue(prompt.contains("金币：1000"));
        assertTrue(prompt.contains("力量：10"));
        assertTrue(prompt.contains("重击"));
        assertFalse(prompt.contains("新玩家"));
        assertFalse(prompt.contains("可选职业"));

        verify(configDataManager).getRole("warrior");
        verify(configDataManager).getSkill("skill1");
    }

    @Test
    void testGenerateBackgroundPrompt_PlayerWithEquipment_ShouldShowEquipment() {
        // Arrange
        Player player = createTestPlayer();

        Equipment weapon = new Equipment();
        weapon.setId("sword1");
        weapon.setName("铁剑");
        weapon.setInstanceNumber(1L);
        weapon.setRarity(com.heibai.clawworld.domain.item.Rarity.COMMON);
        weapon.setSlot(Equipment.EquipmentSlot.RIGHT_HAND);

        player.setEquipment(new HashMap<>());
        player.getEquipment().put(Equipment.EquipmentSlot.RIGHT_HAND, weapon);

        RoleConfig warrior = testRoles.get(0);

        when(configDataManager.getAllMaps()).thenReturn(testMaps);
        when(configDataManager.getAllWaypoints()).thenReturn(testWaypoints);
        when(configDataManager.getMap("map1")).thenReturn(testMaps.get(0));
        when(configDataManager.getMap("map2")).thenReturn(testMaps.get(1));
        when(configDataManager.getRole("warrior")).thenReturn(warrior);

        // Act
        String prompt = backgroundPromptService.generateBackgroundPrompt(player);

        // Assert
        assertTrue(prompt.contains("装备："));
        assertTrue(prompt.contains("右手"));
        assertTrue(prompt.contains("铁剑#1"));
        assertTrue(prompt.contains("普通"));
    }

    @Test
    void testGenerateBackgroundPrompt_MapWithWaypoints_ShouldShowConnections() {
        // Arrange
        when(configDataManager.getAllMaps()).thenReturn(testMaps);
        when(configDataManager.getAllWaypoints()).thenReturn(testWaypoints);
        when(configDataManager.getAllRoles()).thenReturn(testRoles);
        when(configDataManager.getMap("map1")).thenReturn(testMaps.get(0));
        when(configDataManager.getMap("map2")).thenReturn(testMaps.get(1));

        // Act
        String prompt = backgroundPromptService.generateBackgroundPrompt(null);

        // Assert
        assertTrue(prompt.contains("传送点："));
        assertTrue(prompt.contains("村口传送点"));
        assertTrue(prompt.contains("森林入口"));
        assertTrue(prompt.contains("可前往"));
    }

    @Test
    void testGenerateBackgroundPrompt_PlayerWithFreeAttributePoints_ShouldShowPoints() {
        // Arrange
        Player player = createTestPlayer();
        player.setFreeAttributePoints(5);

        RoleConfig warrior = testRoles.get(0);

        when(configDataManager.getAllMaps()).thenReturn(testMaps);
        when(configDataManager.getAllWaypoints()).thenReturn(testWaypoints);
        when(configDataManager.getMap("map1")).thenReturn(testMaps.get(0));
        when(configDataManager.getMap("map2")).thenReturn(testMaps.get(1));
        when(configDataManager.getRole("warrior")).thenReturn(warrior);

        // Act
        String prompt = backgroundPromptService.generateBackgroundPrompt(player);

        // Assert
        assertTrue(prompt.contains("可分配属性点：5"));
    }

    @Test
    void testGenerateBackgroundPrompt_ShouldContainAllCommands() {
        // Arrange
        when(configDataManager.getAllMaps()).thenReturn(testMaps);
        when(configDataManager.getAllWaypoints()).thenReturn(testWaypoints);
        when(configDataManager.getAllRoles()).thenReturn(testRoles);
        when(configDataManager.getMap("map1")).thenReturn(testMaps.get(0));
        when(configDataManager.getMap("map2")).thenReturn(testMaps.get(1));

        // Act
        String prompt = backgroundPromptService.generateBackgroundPrompt(null);

        // Assert
        // 检查各个窗口的指令
        assertTrue(prompt.contains("inspect"));
        assertTrue(prompt.contains("move"));
        assertTrue(prompt.contains("interact"));
        assertTrue(prompt.contains("cast"));
        assertTrue(prompt.contains("trade"));
        assertTrue(prompt.contains("party"));
        assertTrue(prompt.contains("attribute add"));
    }

    /**
     * 创建测试玩家
     */
    private Player createTestPlayer() {
        Player player = new Player();
        player.setId("player1");
        player.setName("测试玩家");
        player.setRoleId("warrior");
        player.setLevel(5);
        player.setExperience(500);
        player.setGold(1000);

        player.setStrength(10);
        player.setAgility(8);
        player.setIntelligence(5);
        player.setVitality(12);
        player.setFreeAttributePoints(0);

        player.setMaxHealth(200);
        player.setCurrentHealth(200);
        player.setMaxMana(80);
        player.setCurrentMana(80);
        player.setPhysicalAttack(35);
        player.setPhysicalDefense(25);
        player.setMagicAttack(10);
        player.setMagicDefense(15);
        player.setSpeed(116);
        player.setCritRate(0.04);
        player.setCritDamage(0.08);
        player.setHitRate(1.018);
        player.setDodgeRate(0.016);

        player.setSkills(Arrays.asList("skill1"));
        player.setInventory(new ArrayList<>());

        return player;
    }
}

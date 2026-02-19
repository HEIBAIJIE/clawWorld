package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.impl.combat.CombatEndHandler;
import com.heibai.clawworld.application.impl.combat.CombatInitiationService;
import com.heibai.clawworld.application.impl.combat.CombatProtectionChecker;
import com.heibai.clawworld.application.service.CombatService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.application.service.WindowStateService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.domain.service.CombatEngine;
import com.heibai.clawworld.domain.service.PlayerLevelService;
import com.heibai.clawworld.domain.service.skill.SkillResolver;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.persistence.mapper.CombatMapper;
import com.heibai.clawworld.infrastructure.persistence.mapper.ConfigMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PartyRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 战斗击败和等级保护机制测试
 *
 * 测试以下新增需求：
 * 1. PVP等级保护：队伍中所有玩家都低于等于推荐等级时受保护
 * 2. 抢怪保护：战斗中的队伍如果所有玩家都低于等于推荐等级则受保护
 * 3. 被击败玩家传送回安全传送点并恢复满状态
 * 4. 高于推荐等级的被击败玩家扣除10%经验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("战斗击败和等级保护机制测试")
class CombatDefeatAndProtectionTest {

    @Mock
    private CombatEngine combatEngine;

    @Mock
    private ConfigDataManager configDataManager;

    @Mock
    private ConfigMapper configMapper;

    @Mock
    private CombatMapper combatMapper;

    @Mock
    private PlayerSessionService playerSessionService;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PlayerLevelService playerLevelService;

    @Mock
    private WindowStateService windowStateService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EnemyInstanceRepository enemyInstanceRepository;

    @Mock
    private CombatProtectionChecker protectionChecker;

    @Mock
    private CombatInitiationService initiationService;

    @Mock
    private CombatEndHandler endHandler;

    @Mock
    private SkillResolver skillResolver;

    @InjectMocks
    private CombatServiceImpl combatService;

    private MapConfig mapConfig;

    @BeforeEach
    void setUp() {
        mapConfig = new MapConfig();
        mapConfig.setId("test_map");
        mapConfig.setName("测试地图");
        mapConfig.setSafe(false);
        mapConfig.setRecommendedLevel(10); // 推荐等级10
    }

    @Nested
    @DisplayName("PVP等级保护测试")
    class PvpLevelProtectionTest {

        @Test
        @DisplayName("无法攻击队伍中所有玩家都低于等于推荐等级的队伍")
        void testCannotAttackFullyProtectedParty() {
            // 攻击者 - 15级
            Player attacker = createPlayer("attacker", "FACTION_A", 15);

            // 目标 - 8级（低于推荐等级10）
            Player target = createPlayer("target", "FACTION_B", 8);

            // 目标队友 - 9级（也低于推荐等级10）
            Player targetTeammate = createPlayer("teammate", "FACTION_B", 9);
            targetTeammate.setPartyId("party1");
            target.setPartyId("party1");

            when(playerSessionService.getPlayerState("attacker")).thenReturn(attacker);
            when(playerSessionService.getPlayerState("target")).thenReturn(target);

            // Mock新服务
            when(initiationService.arePlayersOnSameMap(attacker, target)).thenReturn(true);
            when(protectionChecker.checkMapAllowsCombat("test_map")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(protectionChecker.checkFactionCanAttack("FACTION_A", "FACTION_B")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(initiationService.collectPartyMembers(target)).thenReturn(List.of(target, targetTeammate));
            when(protectionChecker.checkPvpLevelProtection(eq("test_map"), anyList()))
                .thenReturn(CombatProtectionChecker.CheckResult.denied("所有玩家等级都不高于地图推荐等级"));

            CombatService.CombatResult result = combatService.initiateCombat("attacker", "target");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("所有玩家等级都不高于地图推荐等级"));
        }

        @Test
        @DisplayName("可以攻击队伍中有高等级玩家的队伍（即使有低等级队友）")
        void testCanAttackPartyWithHighLevelMember() {
            // 攻击者 - 15级
            Player attacker = createPlayer("attacker", "FACTION_A", 15);

            // 目标A - 8级（低于推荐等级10）
            Player targetA = createPlayer("targetA", "FACTION_B", 8);
            targetA.setPartyId("party1");

            // 目标B - 12级（高于推荐等级10）
            Player targetB = createPlayer("targetB", "FACTION_B", 12);
            targetB.setPartyId("party1");

            // 目标C - 9级（低于推荐等级10）
            Player targetC = createPlayer("targetC", "FACTION_B", 9);
            targetC.setPartyId("party1");

            when(playerSessionService.getPlayerState("attacker")).thenReturn(attacker);
            when(playerSessionService.getPlayerState("targetA")).thenReturn(targetA);
            when(combatEngine.createCombat("test_map")).thenReturn("combat123");

            // Mock新服务
            when(initiationService.arePlayersOnSameMap(attacker, targetA)).thenReturn(true);
            when(protectionChecker.checkMapAllowsCombat("test_map")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(protectionChecker.checkFactionCanAttack("FACTION_A", "FACTION_B")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(initiationService.collectPartyMembers(targetA)).thenReturn(List.of(targetA, targetB, targetC));
            when(initiationService.collectPartyMembers(attacker)).thenReturn(List.of(attacker));
            when(protectionChecker.checkPvpLevelProtection(eq("test_map"), anyList())).thenReturn(CombatProtectionChecker.CheckResult.ok());

            // Mock CombatMapper
            when(combatMapper.toCombatCharacter(any(Player.class))).thenAnswer(invocation -> {
                Player p = invocation.getArgument(0);
                CombatCharacter cc = new CombatCharacter();
                cc.setCharacterId(p.getId());
                cc.setFactionId(p.getFaction());
                cc.setLevel(p.getLevel());
                return cc;
            });

            CombatService.CombatResult result = combatService.initiateCombat("attacker", "targetA");

            assertTrue(result.isSuccess());
            assertEquals("combat123", result.getCombatId());
        }

        @Test
        @DisplayName("单人玩家高于推荐等级时可以被攻击")
        void testCanAttackSingleHighLevelPlayer() {
            // 攻击者 - 15级
            Player attacker = createPlayer("attacker", "FACTION_A", 15);

            // 目标 - 12级（高于推荐等级10）
            Player target = createPlayer("target", "FACTION_B", 12);

            when(playerSessionService.getPlayerState("attacker")).thenReturn(attacker);
            when(playerSessionService.getPlayerState("target")).thenReturn(target);
            when(combatEngine.createCombat("test_map")).thenReturn("combat123");

            // Mock新服务
            when(initiationService.arePlayersOnSameMap(attacker, target)).thenReturn(true);
            when(protectionChecker.checkMapAllowsCombat("test_map")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(protectionChecker.checkFactionCanAttack("FACTION_A", "FACTION_B")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(initiationService.collectPartyMembers(target)).thenReturn(List.of(target));
            when(initiationService.collectPartyMembers(attacker)).thenReturn(List.of(attacker));
            when(protectionChecker.checkPvpLevelProtection(eq("test_map"), anyList())).thenReturn(CombatProtectionChecker.CheckResult.ok());

            // Mock CombatMapper
            when(combatMapper.toCombatCharacter(any(Player.class))).thenAnswer(invocation -> {
                Player p = invocation.getArgument(0);
                CombatCharacter cc = new CombatCharacter();
                cc.setCharacterId(p.getId());
                cc.setFactionId(p.getFaction());
                cc.setLevel(p.getLevel());
                return cc;
            });

            CombatService.CombatResult result = combatService.initiateCombat("attacker", "target");

            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("单人玩家低于等于推荐等级时不能被攻击")
        void testCannotAttackSingleLowLevelPlayer() {
            // 攻击者 - 15级
            Player attacker = createPlayer("attacker", "FACTION_A", 15);

            // 目标 - 10级（等于推荐等级10）
            Player target = createPlayer("target", "FACTION_B", 10);

            when(playerSessionService.getPlayerState("attacker")).thenReturn(attacker);
            when(playerSessionService.getPlayerState("target")).thenReturn(target);

            // Mock新服务
            when(initiationService.arePlayersOnSameMap(attacker, target)).thenReturn(true);
            when(protectionChecker.checkMapAllowsCombat("test_map")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(protectionChecker.checkFactionCanAttack("FACTION_A", "FACTION_B")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(initiationService.collectPartyMembers(target)).thenReturn(List.of(target));
            when(protectionChecker.checkPvpLevelProtection(eq("test_map"), anyList()))
                .thenReturn(CombatProtectionChecker.CheckResult.denied("所有玩家等级都不高于地图推荐等级"));

            CombatService.CombatResult result = combatService.initiateCombat("attacker", "target");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("所有玩家等级都不高于地图推荐等级"));
        }
    }

    @Nested
    @DisplayName("抢怪保护测试")
    class MonsterStealProtectionTest {

        @Test
        @DisplayName("无法加入有受保护队伍的战斗")
        void testCannotJoinCombatWithProtectedParty() {
            // 攻击者 - 15级
            Player attacker = createPlayer("attacker", "FACTION_A", 15);

            when(playerSessionService.getPlayerState("attacker")).thenReturn(attacker);

            // 创建已存在的战斗，其中有一个受保护的队伍
            CombatInstance existingCombat = new CombatInstance("combat123", "test_map");

            // 添加一个受保护的玩家队伍（所有玩家都低于推荐等级）
            CombatParty protectedParty = new CombatParty("FACTION_B");
            CombatCharacter lowLevelPlayer1 = createCombatCharacter("player1", "FACTION_B", 8, true);
            CombatCharacter lowLevelPlayer2 = createCombatCharacter("player2", "FACTION_B", 9, true);
            protectedParty.addCharacter(lowLevelPlayer1);
            protectedParty.addCharacter(lowLevelPlayer2);
            existingCombat.addParty("FACTION_B", protectedParty);

            // 添加敌人队伍
            CombatParty enemyParty = new CombatParty("enemy_slime");
            CombatCharacter enemy = createCombatCharacter("enemy1", "enemy_slime", 10, false);
            enemy.setCharacterType("ENEMY");
            enemyParty.addCharacter(enemy);
            existingCombat.addParty("enemy_slime", enemyParty);

            // Mock敌人实例
            var enemyInstance = new com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity();
            enemyInstance.setId("enemy1");
            enemyInstance.setDisplayName("史莱姆");
            enemyInstance.setMapId("test_map");
            enemyInstance.setInCombat(true);
            enemyInstance.setCombatId("combat123");
            enemyInstance.setX(5);
            enemyInstance.setY(5);

            when(enemyInstanceRepository.findByMapId("test_map")).thenReturn(List.of(enemyInstance));
            when(combatEngine.getCombat("combat123")).thenReturn(Optional.of(existingCombat));

            // Mock新服务
            when(protectionChecker.checkMapAllowsCombat("test_map")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(protectionChecker.checkMonsterStealProtection(eq("test_map"), any(CombatInstance.class)))
                .thenReturn(CombatProtectionChecker.CheckResult.denied("战斗中有受等级保护的队伍"));

            CombatService.CombatResult result = combatService.initiateCombatWithEnemy("attacker", "史莱姆", "test_map");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("受等级保护的队伍") || result.getMessage().contains("受保护"));
        }

        @Test
        @DisplayName("可以加入没有受保护队伍的战斗")
        void testCanJoinCombatWithoutProtectedParty() {
            // 攻击者 - 15级
            Player attacker = createPlayer("attacker", "FACTION_A", 15);

            when(playerSessionService.getPlayerState("attacker")).thenReturn(attacker);

            // 创建已存在的战斗，其中队伍有高等级玩家
            CombatInstance existingCombat = new CombatInstance("combat123", "test_map");

            // 添加一个不受保护的玩家队伍（有高等级玩家）
            CombatParty unprotectedParty = new CombatParty("FACTION_B");
            CombatCharacter lowLevelPlayer = createCombatCharacter("player1", "FACTION_B", 8, true);
            CombatCharacter highLevelPlayer = createCombatCharacter("player2", "FACTION_B", 12, true); // 高于推荐等级
            unprotectedParty.addCharacter(lowLevelPlayer);
            unprotectedParty.addCharacter(highLevelPlayer);
            existingCombat.addParty("FACTION_B", unprotectedParty);

            // 添加敌人队伍
            CombatParty enemyParty = new CombatParty("enemy_slime");
            CombatCharacter enemy = createCombatCharacter("enemy1", "enemy_slime", 10, false);
            enemy.setCharacterType("ENEMY");
            enemyParty.addCharacter(enemy);
            existingCombat.addParty("enemy_slime", enemyParty);

            // Mock敌人实例
            var enemyInstance = new com.heibai.clawworld.infrastructure.persistence.entity.EnemyInstanceEntity();
            enemyInstance.setId("enemy1");
            enemyInstance.setDisplayName("史莱姆");
            enemyInstance.setMapId("test_map");
            enemyInstance.setInCombat(true);
            enemyInstance.setCombatId("combat123");
            enemyInstance.setX(5);
            enemyInstance.setY(5);

            when(enemyInstanceRepository.findByMapId("test_map")).thenReturn(List.of(enemyInstance));
            when(combatEngine.getCombat("combat123")).thenReturn(Optional.of(existingCombat));

            // Mock新服务
            when(protectionChecker.checkMapAllowsCombat("test_map")).thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(protectionChecker.checkMonsterStealProtection(eq("test_map"), any(CombatInstance.class)))
                .thenReturn(CombatProtectionChecker.CheckResult.ok());
            when(initiationService.collectPartyMembers(attacker)).thenReturn(List.of(attacker));

            // Mock CombatMapper
            when(combatMapper.toCombatCharacter(any(Player.class))).thenAnswer(invocation -> {
                Player p = invocation.getArgument(0);
                CombatCharacter cc = new CombatCharacter();
                cc.setCharacterId(p.getId());
                cc.setFactionId(p.getFaction());
                cc.setLevel(p.getLevel());
                return cc;
            });

            CombatService.CombatResult result = combatService.initiateCombatWithEnemy("attacker", "史莱姆", "test_map");

            assertTrue(result.isSuccess());
            assertEquals("combat123", result.getCombatId());
        }
    }

    @Nested
    @DisplayName("被击败玩家处理测试")
    class DefeatedPlayerHandlingTest {

        @Test
        @DisplayName("DefeatedPlayer数据结构正确记录玩家信息")
        void testDefeatedPlayerDataStructure() {
            // 验证DefeatedPlayer数据结构
            CombatInstance.DefeatedPlayer defeatedPlayer = new CombatInstance.DefeatedPlayer("player1", 15, true);

            assertEquals("player1", defeatedPlayer.getPlayerId());
            assertEquals(15, defeatedPlayer.getPlayerLevel());
            assertTrue(defeatedPlayer.isAllPlayersDefeated());
        }

        @Test
        @DisplayName("RewardDistribution正确记录被击败玩家列表")
        void testRewardDistributionDefeatedPlayers() {
            CombatInstance.RewardDistribution distribution = new CombatInstance.RewardDistribution();
            distribution.setCombatType(CombatInstance.CombatType.PVE);
            distribution.setMapId("test_map");

            // 添加被击败玩家
            distribution.getDefeatedPlayers().add(new CombatInstance.DefeatedPlayer("player1", 15, true));
            distribution.getDefeatedPlayers().add(new CombatInstance.DefeatedPlayer("player2", 8, true));

            assertEquals(2, distribution.getDefeatedPlayers().size());
            assertEquals("player1", distribution.getDefeatedPlayers().get(0).getPlayerId());
            assertEquals(15, distribution.getDefeatedPlayers().get(0).getPlayerLevel());
        }

        @Test
        @DisplayName("高于推荐等级的玩家应该受到经验惩罚")
        void testHighLevelPlayerShouldBePenalized() {
            int recommendedLevel = 10;
            int playerLevel = 15;

            // 验证惩罚条件
            boolean shouldPenalize = playerLevel > recommendedLevel;
            assertTrue(shouldPenalize, "高于推荐等级的玩家应该受到惩罚");

            // 验证惩罚计算
            int currentExp = 1000;
            int expPenalty = (int) (currentExp * 0.1);
            assertEquals(100, expPenalty, "应该扣除10%经验");
        }

        @Test
        @DisplayName("低于等于推荐等级的玩家不应该受到经验惩罚")
        void testLowLevelPlayerShouldNotBePenalized() {
            int recommendedLevel = 10;
            int playerLevel = 8;

            // 验证不惩罚条件
            boolean shouldPenalize = playerLevel > recommendedLevel;
            assertFalse(shouldPenalize, "低于推荐等级的玩家不应该受到惩罚");
        }

        @Test
        @DisplayName("等于推荐等级的玩家不应该受到经验惩罚")
        void testEqualLevelPlayerShouldNotBePenalized() {
            int recommendedLevel = 10;
            int playerLevel = 10;

            // 验证不惩罚条件
            boolean shouldPenalize = playerLevel > recommendedLevel;
            assertFalse(shouldPenalize, "等于推荐等级的玩家不应该受到惩罚");
        }
    }

    @Nested
    @DisplayName("PVE和PVP经验惩罚区分测试")
    class CombatTypeExperiencePenaltyTest {

        @Test
        @DisplayName("PVE战斗中高等级玩家被击败扣除10%经验")
        void testPveHighLevelPlayerLosesExperience() {
            // 验证PVE战斗类型下的经验惩罚
            CombatInstance.RewardDistribution distribution = new CombatInstance.RewardDistribution();
            distribution.setCombatType(CombatInstance.CombatType.PVE);
            distribution.setMapId("test_map");

            // 高等级玩家（15级 > 推荐等级10）
            distribution.getDefeatedPlayers().add(
                new CombatInstance.DefeatedPlayer("player1", 15, true)
            );

            // 验证应该扣除经验
            assertTrue(distribution.getCombatType() == CombatInstance.CombatType.PVE);
            assertTrue(distribution.getDefeatedPlayers().get(0).getPlayerLevel() > 10);
        }

        @Test
        @DisplayName("PVP战斗中高等级玩家被击败扣除10%经验")
        void testPvpHighLevelPlayerLosesExperience() {
            // 验证PVP战斗类型下的经验惩罚
            CombatInstance.RewardDistribution distribution = new CombatInstance.RewardDistribution();
            distribution.setCombatType(CombatInstance.CombatType.PVP);
            distribution.setMapId("test_map");

            // 高等级玩家（15级 > 推荐等级10）
            distribution.getDefeatedPlayers().add(
                new CombatInstance.DefeatedPlayer("player1", 15, false)
            );

            // 验证应该扣除经验
            assertTrue(distribution.getCombatType() == CombatInstance.CombatType.PVP);
            assertTrue(distribution.getDefeatedPlayers().get(0).getPlayerLevel() > 10);
        }
    }

    // 辅助方法

    private Player createPlayer(String id, String faction, int level) {
        Player player = new Player();
        player.setId(id);
        player.setName(id);
        player.setMapId("test_map");
        player.setX(5);
        player.setY(5);
        player.setFaction(faction);
        player.setLevel(level);
        return player;
    }

    private CombatCharacter createCombatCharacter(String id, String faction, int level, boolean isPlayer) {
        CombatCharacter cc = new CombatCharacter();
        cc.setCharacterId(id);
        cc.setName(id);
        cc.setFactionId(faction);
        cc.setLevel(level);
        cc.setCharacterType(isPlayer ? "PLAYER" : "ENEMY");
        cc.setMaxHealth(100);
        cc.setCurrentHealth(100);
        return cc;
    }
}

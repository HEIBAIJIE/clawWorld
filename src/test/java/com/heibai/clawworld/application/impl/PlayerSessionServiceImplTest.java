package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.WindowContentService;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.infrastructure.factory.MapInitializationService;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PartyEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PartyRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.application.service.PlayerSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerSessionServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private ConfigDataManager configDataManager;

    @Mock
    private WindowContentService windowContentService;

    @Mock
    private MapInitializationService mapInitializationService;

    @InjectMocks
    private PlayerSessionServiceImpl playerSessionService;

    private AccountEntity testAccount;
    private PlayerEntity testPlayer;
    private RoleConfig testRole;

    @BeforeEach
    void setUp() {
        testAccount = new AccountEntity();
        testAccount.setId("account1");
        testAccount.setUsername("testuser");
        testAccount.setSessionId("session123");
        testAccount.setPlayerId(null);

        testPlayer = new PlayerEntity();
        testPlayer.setId("player1");
        testPlayer.setRoleId("warrior");
        testPlayer.setLevel(1);
        testPlayer.setGold(100);
        testPlayer.setStrength(0);
        testPlayer.setAgility(0);
        testPlayer.setIntelligence(0);
        testPlayer.setVitality(0);
        testPlayer.setFreeAttributePoints(5);

        testRole = new RoleConfig();
        testRole.setId("warrior");
        testRole.setName("战士");
        testRole.setBaseHealth(100);
        testRole.setBaseMana(50);
        testRole.setBasePhysicalAttack(20);
        testRole.setBasePhysicalDefense(15);
        testRole.setBaseMagicAttack(5);
        testRole.setBaseMagicDefense(10);
        testRole.setBaseSpeed(100);
        testRole.setBaseCritRate(0.05);
        testRole.setBaseCritDamage(0.5);
        testRole.setBaseHitRate(1.0);
        testRole.setBaseDodgeRate(0.0);
        testRole.setHealthPerLevel(10);
        testRole.setManaPerLevel(5);
        testRole.setPhysicalAttackPerLevel(2);
        testRole.setPhysicalDefensePerLevel(1.5);
        testRole.setMagicAttackPerLevel(0.5);
        testRole.setMagicDefensePerLevel(1);
        testRole.setSpeedPerLevel(1);
        testRole.setCritRatePerLevel(0.001);
        testRole.setCritDamagePerLevel(0.01);
        testRole.setHitRatePerLevel(0.001);
        testRole.setDodgeRatePerLevel(0.001);
    }

    @Test
    void testRegisterPlayer_Success() {
        // Arrange
        when(accountRepository.findBySessionId("session123")).thenReturn(Optional.of(testAccount));
        when(accountRepository.existsByNickname("TestPlayer")).thenReturn(false);
        when(configDataManager.getAllRoles()).thenReturn(Collections.singletonList(testRole));
        when(playerRepository.save(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(partyRepository.save(any(PartyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerMapper.toEntity(any(Player.class))).thenReturn(testPlayer);

        // Mock map and window content
        GameMap mockMap = new GameMap();
        mockMap.setId("starter_village");
        mockMap.setName("新手村");
        when(mapInitializationService.getMap("starter_village")).thenReturn(mockMap);
        when(windowContentService.generateMapWindowContent(any(Player.class), any(GameMap.class)))
            .thenReturn("地图窗口内容");

        // Act
        PlayerSessionService.SessionResult result = playerSessionService.registerPlayer("session123", "战士", "TestPlayer");

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("注册成功"));
        assertNotNull(result.getPlayerId());
        assertNotNull(result.getWindowContent());
        verify(playerRepository, atLeastOnce()).save(any(PlayerEntity.class));
        verify(partyRepository).save(any(PartyEntity.class));
        verify(accountRepository).save(any(AccountEntity.class));
    }

    @Test
    void testRegisterPlayer_NicknameAlreadyExists() {
        // Arrange
        when(accountRepository.findBySessionId("session123")).thenReturn(Optional.of(testAccount));
        when(accountRepository.existsByNickname("TestPlayer")).thenReturn(true);

        // Act
        PlayerSessionService.SessionResult result = playerSessionService.registerPlayer("session123", "战士", "TestPlayer");

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("昵称已被使用", result.getMessage());
        verify(playerRepository, never()).save(any());
    }

    @Test
    void testRegisterPlayer_InvalidRole() {
        // Arrange
        when(accountRepository.findBySessionId("session123")).thenReturn(Optional.of(testAccount));
        when(accountRepository.existsByNickname("TestPlayer")).thenReturn(false);
        when(configDataManager.getAllRoles()).thenReturn(Collections.singletonList(testRole));

        // Act
        PlayerSessionService.SessionResult result = playerSessionService.registerPlayer("session123", "无效职业", "TestPlayer");

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("职业不存在", result.getMessage());
    }

    @Test
    void testAddAttribute_Success() {
        // Arrange
        Player player = new Player();
        player.setId("player1");
        player.setRoleId("warrior");
        player.setStrength(0);
        player.setFreeAttributePoints(5);

        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(playerMapper.toDomain(testPlayer)).thenReturn(player);
        when(configDataManager.getRole("warrior")).thenReturn(testRole);
        when(playerMapper.toEntity(any(Player.class))).thenReturn(testPlayer);
        when(playerRepository.save(any(PlayerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PlayerSessionService.OperationResult result = playerSessionService.addAttribute("player1", "str", 3);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("添加属性点成功"));
        verify(playerRepository).save(any(PlayerEntity.class));
    }

    @Test
    void testAddAttribute_InsufficientPoints() {
        // Arrange
        Player player = new Player();
        player.setId("player1");
        player.setFreeAttributePoints(2);

        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(playerMapper.toDomain(testPlayer)).thenReturn(player);

        // Act
        PlayerSessionService.OperationResult result = playerSessionService.addAttribute("player1", "str", 5);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("可用属性点不足", result.getMessage());
        verify(playerRepository, never()).save(any());
    }

    @Test
    void testLogout_Success() {
        // Arrange
        when(accountRepository.findBySessionId("session123")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PlayerSessionService.OperationResult result = playerSessionService.logout("session123");

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("下线成功", result.getMessage());
        verify(accountRepository).save(argThat(account ->
            !account.isOnline() &&
            account.getSessionId() == null &&
            account.getLastLogoutTime() != null
        ));
    }

    @Test
    void testWait_Success() {
        // Act
        PlayerSessionService.OperationResult result = playerSessionService.wait("player1", 1);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("等待"));
    }

    @Test
    void testWait_InvalidDuration() {
        // Act
        PlayerSessionService.OperationResult result = playerSessionService.wait("player1", 100);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("等待时间必须在1-60秒之间", result.getMessage());
    }
}

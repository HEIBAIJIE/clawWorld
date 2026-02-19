package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.service.PlayerLevelService;
import com.heibai.clawworld.infrastructure.factory.MapInitializationService;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.interfaces.log.MapWindowLogGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthService单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PlayerSessionService playerSessionService;

    @Mock
    private MapWindowLogGenerator mapWindowLogGenerator;

    @Mock
    private MapInitializationService mapInitializationService;

    @Mock
    private MapEntityService mapEntityService;

    @Mock
    private ChatService chatService;

    @Mock
    private PlayerLevelService playerLevelService;

    @Mock
    private com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository tradeRepository;

    @Mock
    private com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository playerRepository;

    @InjectMocks
    private AuthService authService;

    private AccountEntity testAccount;

    @BeforeEach
    void setUp() {
        testAccount = new AccountEntity();
        testAccount.setId("account1");
        testAccount.setUsername("testuser");
        testAccount.setPassword("testpass");
        testAccount.setPlayerId("player1");
    }

    @Test
    void testLoginOrRegister_NewUser_ShouldRegisterSuccessfully() {
        // Arrange
        when(accountRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("newuser", "newpass");

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("登录成功", result.getMessage());
        assertNotNull(result.getSessionId());
        assertNotNull(result.getContent());
        assertTrue(result.isNewUser());

        verify(accountRepository).save(any(AccountEntity.class));
    }

    @Test
    void testLoginOrRegister_ExistingUser_CorrectPassword_ShouldLoginSuccessfully() {
        // Arrange
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Player mockPlayer = new Player();
        mockPlayer.setId("player1");
        mockPlayer.setMapId("starter_village");
        mockPlayer.setRoleId("warrior");
        when(playerSessionService.getPlayerState("player1")).thenReturn(mockPlayer);
        when(playerLevelService.processLevelUp(any(Player.class))).thenReturn(false);

        // Mock trade cleanup
        when(tradeRepository.findActiveTradesByPlayerId(any(), anyString())).thenReturn(new ArrayList<>());

        GameMap mockMap = new GameMap();
        mockMap.setId("starter_village");
        mockMap.setName("新手村");
        when(mapInitializationService.getMap("starter_village")).thenReturn(mockMap);
        when(mapEntityService.getMapEntities(anyString())).thenReturn(new ArrayList<>());
        when(chatService.getChatHistory(anyString())).thenReturn(new ArrayList<>());

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("testuser", "testpass");

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("登录成功", result.getMessage());
        assertNotNull(result.getSessionId());
        assertNotNull(result.getContent());
        assertFalse(result.isNewUser());

        verify(accountRepository).save(any(AccountEntity.class));
        verify(playerSessionService, times(2)).getPlayerState("player1"); // 调用两次：升级前和升级后
        verify(playerLevelService).processLevelUp(any(Player.class));
    }

    @Test
    void testLoginOrRegister_ExistingUser_WrongPassword_ShouldFail() {
        // Arrange
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(testAccount));

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("testuser", "wrongpass");

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("密码错误", result.getMessage());
        assertNull(result.getSessionId());
        assertNull(result.getContent());

        verify(accountRepository, never()).save(any());
    }

    @Test
    void testLoginOrRegister_ExistingUser_NoPlayer_ShouldLoginWithoutPlayerData() {
        // Arrange
        testAccount.setPlayerId(null);
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("testuser", "testpass");

        // Assert
        assertTrue(result.isSuccess());
        assertNotNull(result.getSessionId());
        assertNotNull(result.getContent());
        assertTrue(result.isNewUser());

        verify(playerSessionService, never()).getPlayerState(anyString());
    }

    @Test
    void testGetAccountBySessionId_ShouldReturnAccount() {
        // Arrange
        String sessionId = "session123";
        testAccount.setSessionId(sessionId);
        when(accountRepository.findBySessionId(sessionId)).thenReturn(Optional.of(testAccount));

        // Act
        Optional<AccountEntity> result = authService.getAccountBySessionId(sessionId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(accountRepository).findBySessionId(sessionId);
    }

    @Test
    void testLogout_ShouldUpdateAccountStatus() {
        // Arrange
        String sessionId = "session123";
        testAccount.setSessionId(sessionId);
        testAccount.setOnline(true);
        when(accountRepository.findBySessionId(sessionId)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock trade cleanup
        when(tradeRepository.findActiveTradesByPlayerId(any(), anyString())).thenReturn(new ArrayList<>());

        // Act
        authService.logout(sessionId);

        // Assert
        verify(accountRepository).save(argThat(account ->
            !account.isOnline() &&
            account.getSessionId() == null &&
            account.getLastLogoutTime() != null
        ));
    }

    @Test
    void testLogout_NonExistentSession_ShouldNotThrowException() {
        // Arrange
        when(accountRepository.findBySessionId("invalid")).thenReturn(Optional.empty());

        // Act & Assert
        assertDoesNotThrow(() -> authService.logout("invalid"));
        verify(accountRepository, never()).save(any());
    }
}

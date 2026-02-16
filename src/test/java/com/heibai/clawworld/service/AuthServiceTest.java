package com.heibai.clawworld.service;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.persistence.entity.AccountEntity;
import com.heibai.clawworld.persistence.entity.PlayerEntity;
import com.heibai.clawworld.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.persistence.repository.AccountRepository;
import com.heibai.clawworld.persistence.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private PlayerRepository playerRepository;

    @Mock
    private BackgroundPromptService backgroundPromptService;

    @Mock
    private PlayerMapper playerMapper;

    @InjectMocks
    private AuthService authService;

    private AccountEntity testAccount;
    private PlayerEntity testPlayer;

    @BeforeEach
    void setUp() {
        testAccount = new AccountEntity();
        testAccount.setId("account1");
        testAccount.setUsername("testuser");
        testAccount.setPassword("testpass");
        testAccount.setPlayerId("player1");

        testPlayer = new PlayerEntity();
        testPlayer.setId("player1");
        testPlayer.setRoleId("warrior");
        testPlayer.setLevel(5);
        testPlayer.setGold(1000);
    }

    @Test
    void testLoginOrRegister_NewUser_ShouldRegisterSuccessfully() {
        // Arrange
        when(accountRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(backgroundPromptService.generateBackgroundPrompt(null)).thenReturn("Welcome to ClawWorld!");

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("newuser", "newpass");

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("登录成功", result.getMessage());
        assertNotNull(result.getSessionId());
        assertEquals("Welcome to ClawWorld!", result.getBackgroundPrompt());
        assertTrue(result.isNewUser());

        verify(accountRepository).save(any(AccountEntity.class));
        verify(backgroundPromptService).generateBackgroundPrompt(null);
    }

    @Test
    void testLoginOrRegister_ExistingUser_CorrectPassword_ShouldLoginSuccessfully() {
        // Arrange
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));

        Player mockPlayer = new Player();
        mockPlayer.setId("player1");
        when(playerMapper.toDomain(any(PlayerEntity.class))).thenReturn(mockPlayer);
        when(backgroundPromptService.generateBackgroundPrompt(any())).thenReturn("Welcome back!");

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("testuser", "testpass");

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("登录成功", result.getMessage());
        assertNotNull(result.getSessionId());
        assertEquals("Welcome back!", result.getBackgroundPrompt());
        assertFalse(result.isNewUser());

        verify(accountRepository).save(any(AccountEntity.class));
        verify(playerRepository).findById("player1");
        verify(playerMapper).toDomain(any(PlayerEntity.class));
        verify(backgroundPromptService).generateBackgroundPrompt(any());
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
        assertNull(result.getBackgroundPrompt());

        verify(accountRepository, never()).save(any());
        verify(backgroundPromptService, never()).generateBackgroundPrompt(any());
    }

    @Test
    void testLoginOrRegister_ExistingUser_NoPlayer_ShouldLoginWithoutPlayerData() {
        // Arrange
        testAccount.setPlayerId(null);
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(backgroundPromptService.generateBackgroundPrompt(null)).thenReturn("Welcome!");

        // Act
        AuthService.LoginResult result = authService.loginOrRegister("testuser", "testpass");

        // Assert
        assertTrue(result.isSuccess());
        assertNotNull(result.getSessionId());
        assertTrue(result.isNewUser());

        verify(playerRepository, never()).findById(anyString());
        verify(backgroundPromptService).generateBackgroundPrompt(null);
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

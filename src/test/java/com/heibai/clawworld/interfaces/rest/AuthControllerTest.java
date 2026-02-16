package com.heibai.clawworld.interfaces.rest;

import com.heibai.clawworld.interfaces.dto.LoginRequest;
import com.heibai.clawworld.interfaces.dto.LoginResponse;
import com.heibai.clawworld.interfaces.dto.LogoutRequest;
import com.heibai.clawworld.application.impl.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthController单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void testLogin_Success_ShouldReturn200() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("testpass");

        AuthService.LoginResult loginResult = AuthService.LoginResult.success(
                "session123",
                "Welcome to ClawWorld!",
                false
        );

        when(authService.loginOrRegister("testuser", "testpass")).thenReturn(loginResult);

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("登录成功", response.getBody().getMessage());
        assertEquals("session123", response.getBody().getSessionId());
        assertEquals("Welcome to ClawWorld!", response.getBody().getBackgroundPrompt());

        verify(authService).loginOrRegister("testuser", "testpass");
    }

    @Test
    void testLogin_WrongPassword_ShouldReturn401() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpass");

        AuthService.LoginResult loginResult = AuthService.LoginResult.error("密码错误");

        when(authService.loginOrRegister("testuser", "wrongpass")).thenReturn(loginResult);

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("密码错误", response.getBody().getMessage());

        verify(authService).loginOrRegister("testuser", "wrongpass");
    }

    @Test
    void testLogin_EmptyUsername_ShouldReturn400() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("testpass");

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("用户名不能为空", response.getBody().getMessage());

        verify(authService, never()).loginOrRegister(anyString(), anyString());
    }

    @Test
    void testLogin_NullUsername_ShouldReturn400() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername(null);
        request.setPassword("testpass");

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("用户名不能为空", response.getBody().getMessage());

        verify(authService, never()).loginOrRegister(anyString(), anyString());
    }

    @Test
    void testLogin_EmptyPassword_ShouldReturn400() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("");

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("密码不能为空", response.getBody().getMessage());

        verify(authService, never()).loginOrRegister(anyString(), anyString());
    }

    @Test
    void testLogin_WhitespaceUsername_ShouldReturn400() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("   ");
        request.setPassword("testpass");

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("用户名不能为空", response.getBody().getMessage());

        verify(authService, never()).loginOrRegister(anyString(), anyString());
    }

    @Test
    void testLogin_NewUser_ShouldRegisterAndReturn200() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("newuser");
        request.setPassword("newpass");

        AuthService.LoginResult loginResult = AuthService.LoginResult.success(
                "session456",
                "Welcome new player!",
                true
        );

        when(authService.loginOrRegister("newuser", "newpass")).thenReturn(loginResult);

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("session456", response.getBody().getSessionId());
        assertEquals("Welcome new player!", response.getBody().getBackgroundPrompt());

        verify(authService).loginOrRegister("newuser", "newpass");
    }

    @Test
    void testLogout_ShouldReturn200() {
        // Arrange
        LogoutRequest request = new LogoutRequest();
        request.setSessionId("session123");
        doNothing().when(authService).logout("session123");

        // Act
        ResponseEntity<Void> response = authController.logout(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());

        verify(authService).logout("session123");
    }

    @Test
    void testLogin_TrimsUsername_ShouldCallServiceWithTrimmedValue() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("  testuser  ");
        request.setPassword("testpass");

        AuthService.LoginResult loginResult = AuthService.LoginResult.success(
                "session123",
                "Welcome!",
                false
        );

        when(authService.loginOrRegister("testuser", "testpass")).thenReturn(loginResult);

        // Act
        ResponseEntity<LoginResponse> response = authController.login(request);

        // Assert
        assertEquals(200, response.getStatusCode().value());

        verify(authService).loginOrRegister("testuser", "testpass");
    }
}

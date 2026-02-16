package com.heibai.clawworld.controller;

import com.heibai.clawworld.dto.LoginRequest;
import com.heibai.clawworld.dto.LoginResponse;
import com.heibai.clawworld.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 提供登录接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录或注册接口
     * 根据设计文档第三章第2节：服务器提供游戏登录的REST接口
     *
     * @param request 登录请求，包含用户名和密码
     * @return 登录响应，包含会话ID和背景prompt
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // 验证输入
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(LoginResponse.error("用户名不能为空"));
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(LoginResponse.error("密码不能为空"));
        }

        // 执行登录或注册
        AuthService.LoginResult result = authService.loginOrRegister(
                request.getUsername().trim(),
                request.getPassword()
        );

        if (result.isSuccess()) {
            return ResponseEntity.ok(
                    LoginResponse.success(result.getSessionId(), result.getBackgroundPrompt())
            );
        } else {
            return ResponseEntity.status(401)
                    .body(LoginResponse.error(result.getMessage()));
        }
    }

    /**
     * 登出接口
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Session-Id") String sessionId) {
        authService.logout(sessionId);
        return ResponseEntity.ok().build();
    }
}

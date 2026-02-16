package com.heibai.clawworld.controller;

import com.heibai.clawworld.command.*;
import com.heibai.clawworld.dto.CommandRequest;
import com.heibai.clawworld.dto.CommandResponse;
import com.heibai.clawworld.persistence.entity.AccountEntity;
import com.heibai.clawworld.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 指令控制器
 * 处理玩家发送的指令
 */
@RestController
@RequestMapping("/api/command")
@RequiredArgsConstructor
public class CommandController {

    private final CommandParser commandParser;
    private final CommandExecutor commandExecutor;
    private final AuthService authService;

    /**
     * 执行指令
     * @param sessionId 会话ID（从请求头获取）
     * @param request 指令请求
     * @return 指令执行结果
     */
    @PostMapping("/execute")
    public ResponseEntity<CommandResponse> executeCommand(
            @RequestHeader("Session-Id") String sessionId,
            @RequestBody CommandRequest request) {

        // 验证会话
        Optional<AccountEntity> account = authService.getAccountBySessionId(sessionId);
        if (!account.isPresent() || !account.get().isOnline()) {
            return ResponseEntity.status(401)
                    .body(CommandResponse.error("会话无效或已过期"));
        }

        try {
            // 解析指令
            Command command = commandParser.parse(
                    request.getCommand(),
                    request.getWindowType()
            );

            // 构建执行上下文
            CommandContext context = CommandContext.builder()
                    .sessionId(sessionId)
                    .windowId(request.getWindowId())
                    .playerId(account.get().getPlayerId())
                    .windowType(request.getWindowType())
                    .build();

            // 执行指令
            CommandResult result = commandExecutor.execute(command, context);

            // 返回结果
            if (result.isSuccess()) {
                return ResponseEntity.ok(
                        CommandResponse.success(
                                result.getMessage(),
                                result.isWindowChanged(),
                                result.getNewWindowId(),
                                result.getNewWindowType() != null ? result.getNewWindowType().name() : null,
                                result.getData()
                        )
                );
            } else {
                return ResponseEntity.badRequest()
                        .body(CommandResponse.error(result.getMessage()));
            }

        } catch (CommandParser.CommandParseException e) {
            return ResponseEntity.badRequest()
                    .body(CommandResponse.error("指令解析失败: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(CommandResponse.error("服务器内部错误: " + e.getMessage()));
        }
    }
}

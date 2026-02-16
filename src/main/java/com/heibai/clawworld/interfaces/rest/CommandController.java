package com.heibai.clawworld.interfaces.rest;

import com.heibai.clawworld.interfaces.command.*;
import com.heibai.clawworld.interfaces.dto.CommandRequest;
import com.heibai.clawworld.interfaces.dto.CommandResponse;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.application.impl.AuthService;
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
     * @param request 指令请求（包含sessionId和command）
     * @return 指令执行结果
     */
    @PostMapping("/execute")
    public ResponseEntity<CommandResponse> executeCommand(@RequestBody CommandRequest request) {

        // 验证会话
        Optional<AccountEntity> account = authService.getAccountBySessionId(request.getSessionId());
        if (!account.isPresent() || !account.get().isOnline()) {
            return ResponseEntity.status(401)
                    .body(CommandResponse.error("会话无效或已过期"));
        }

        AccountEntity accountEntity = account.get();

        // 获取当前窗口状态
        String windowId = accountEntity.getCurrentWindowId();
        CommandContext.WindowType windowType = accountEntity.getCurrentWindowType() != null ?
                CommandContext.WindowType.valueOf(accountEntity.getCurrentWindowType()) : null;

        try {
            // 解析指令
            Command command = commandParser.parse(
                    request.getCommand(),
                    windowType
            );

            // 构建执行上下文
            CommandContext context = CommandContext.builder()
                    .sessionId(request.getSessionId())
                    .windowId(windowId)
                    .playerId(accountEntity.getPlayerId())
                    .windowType(windowType)
                    .build();

            // 执行指令
            CommandResult result = commandExecutor.execute(command, context);

            // 如果窗口改变，更新账号的窗口状态
            if (result.isWindowChanged()) {
                authService.updateWindowState(
                        request.getSessionId(),
                        result.getNewWindowId(),
                        result.getNewWindowType() != null ? result.getNewWindowType().name() : null
                );
            }

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

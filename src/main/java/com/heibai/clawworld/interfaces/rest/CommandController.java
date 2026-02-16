package com.heibai.clawworld.interfaces.rest;

import com.heibai.clawworld.interfaces.command.*;
import com.heibai.clawworld.interfaces.dto.CommandRequest;
import com.heibai.clawworld.interfaces.dto.CommandResponse;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.application.impl.AuthService;
import com.heibai.clawworld.application.service.StateService;
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
    private final StateService stateService;

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

            // 如果窗口改变,更新账号的窗口状态
            if (result.isWindowChanged()) {
                authService.updateWindowState(
                        request.getSessionId(),
                        result.getNewWindowType() != null ? result.getNewWindowType().name() : null,
                        result.getNewWindowType() != null ? result.getNewWindowType().name() : null
                );
            }

            // 生成完整的响应文本（包含环境变化）
            String responseText;

            // 根据窗口类型生成状态信息
            if (windowType == CommandContext.WindowType.MAP) {
                // 地图窗口：包含指令结果 + 环境变化
                responseText = stateService.generateMapState(accountEntity.getPlayerId(), result.getMessage());
            } else if (windowType == CommandContext.WindowType.COMBAT) {
                // 战斗窗口：包含指令结果 + 战斗状态
                responseText = stateService.generateCombatState(
                        accountEntity.getPlayerId(),
                        windowId,
                        result.getMessage()
                );
            } else if (windowType == CommandContext.WindowType.TRADE) {
                // 交易窗口：包含指令结果 + 交易状态
                responseText = stateService.generateTradeState(
                        accountEntity.getPlayerId(),
                        windowId,
                        result.getMessage()
                );
            } else {
                // 其他窗口（如注册窗口）：只返回指令结果
                StringBuilder sb = new StringBuilder();
                if (result.getWindowContent() != null && !result.getWindowContent().isEmpty()) {
                    sb.append(result.getWindowContent());
                    sb.append("\n\n");
                }
                sb.append(">>> ").append(result.getMessage());
                responseText = sb.toString();
            }

            // 返回结果
            if (result.isSuccess()) {
                return ResponseEntity.ok(CommandResponse.success(responseText));
            } else {
                return ResponseEntity.badRequest()
                        .body(CommandResponse.error(responseText));
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

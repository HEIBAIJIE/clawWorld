package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 私聊指令
 * say to [player_name] [message]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SayToCommand extends Command {
    private String targetPlayer;
    private String message;

    @Builder
    public SayToCommand(String targetPlayer, String message, String rawCommand) {
        this.targetPlayer = targetPlayer;
        this.message = message;
        setRawCommand(rawCommand);
        setType(CommandType.SAY_TO);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        ChatService.ChatResult result = CommandServiceLocator.getInstance().getChatService()
                .sendPrivateMessage(context.getPlayerId(), targetPlayer, message);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (targetPlayer == null || targetPlayer.trim().isEmpty()) {
            return ValidationResult.error("目标玩家不能为空");
        }
        if (message == null || message.trim().isEmpty()) {
            return ValidationResult.error("消息不能为空");
        }
        if (message.length() > 30) {
            return ValidationResult.error("消息长度不能超过30字");
        }
        return ValidationResult.success();
    }
}

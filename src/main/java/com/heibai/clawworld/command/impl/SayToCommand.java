package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
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
        throw new UnsupportedOperationException("需要注入 ChatService 来执行此指令");
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

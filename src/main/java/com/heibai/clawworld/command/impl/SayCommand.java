package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公屏聊天指令
 * say [channel] [message]
 * channel可以为: world, map, party
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SayCommand extends Command {
    private String channel;
    private String message;

    @Builder
    public SayCommand(String channel, String message, String rawCommand) {
        this.channel = channel;
        this.message = message;
        setRawCommand(rawCommand);
        setType(CommandType.SAY);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 ChatService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (channel == null || channel.trim().isEmpty()) {
            return ValidationResult.error("频道不能为空");
        }
        if (!channel.equals("world") && !channel.equals("map") && !channel.equals("party")) {
            return ValidationResult.error("频道必须是 world, map 或 party");
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

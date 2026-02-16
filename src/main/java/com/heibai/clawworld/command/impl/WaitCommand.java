package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WaitCommand extends Command {
    private int seconds;

    @Builder
    public WaitCommand(int seconds, String rawCommand) {
        this.seconds = seconds;
        setRawCommand(rawCommand);
        setType(CommandType.WAIT);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 PlayerSessionService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (seconds <= 0 || seconds > 60) {
            return ValidationResult.error("等待时间必须在1-60秒之间");
        }
        return ValidationResult.success();
    }
}

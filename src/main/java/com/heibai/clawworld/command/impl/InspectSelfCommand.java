package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查看自身状态指令
 * inspect self
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectSelfCommand extends Command {

    @Builder
    public InspectSelfCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.INSPECT_SELF);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 PlayerSessionService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

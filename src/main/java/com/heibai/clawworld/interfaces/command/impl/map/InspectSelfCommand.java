package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
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
        String detailedStatus = CommandServiceLocator.getInstance().getPlayerSessionService()
                .getPlayerDetailedStatus(context.getPlayerId());
        return CommandResult.success(detailedStatus);
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

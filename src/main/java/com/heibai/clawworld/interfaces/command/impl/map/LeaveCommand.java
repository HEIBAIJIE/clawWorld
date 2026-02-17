package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeaveCommand extends Command {

    @Builder
    public LeaveCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.LEAVE);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PlayerSessionService.OperationResult result = CommandServiceLocator.getInstance().getPlayerSessionService()
                .logout(context.getSessionId());

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

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
        PlayerSessionService.OperationResult result = CommandServiceLocator.getInstance().getPlayerSessionService()
                .wait(context.getPlayerId(), seconds);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (seconds <= 0 || seconds > 60) {
            return ValidationResult.error("等待时间必须在1-60秒之间");
        }
        return ValidationResult.success();
    }
}

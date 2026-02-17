package com.heibai.clawworld.interfaces.command.impl.combat;

import com.heibai.clawworld.application.service.CombatService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WaitCombatCommand extends Command {

    @Builder
    public WaitCombatCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.WAIT_COMBAT);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String combatId = context.getWindowId();

        CombatService.ActionResult result = CommandServiceLocator.getInstance().getCombatService()
                .waitTurn(combatId, context.getPlayerId());

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

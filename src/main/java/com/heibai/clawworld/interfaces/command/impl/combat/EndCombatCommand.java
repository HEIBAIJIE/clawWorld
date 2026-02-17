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
public class EndCombatCommand extends Command {

    @Builder
    public EndCombatCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.END_COMBAT);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String combatId = context.getWindowId();

        CombatService.ActionResult result = CommandServiceLocator.getInstance().getCombatService()
                .forfeit(combatId, context.getPlayerId());

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

package com.heibai.clawworld.interfaces.command.impl.party;

import com.heibai.clawworld.application.service.PartyService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartyLeaveCommand extends Command {

    @Builder
    public PartyLeaveCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.PARTY_LEAVE);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PartyService.PartyResult result = CommandServiceLocator.getInstance().getPartyService()
                .leaveParty(context.getPlayerId());

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

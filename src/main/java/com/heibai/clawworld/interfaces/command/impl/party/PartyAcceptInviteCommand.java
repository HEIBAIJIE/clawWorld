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
public class PartyAcceptInviteCommand extends Command {
    private String inviterName;

    @Builder
    public PartyAcceptInviteCommand(String inviterName, String rawCommand) {
        this.inviterName = inviterName;
        setRawCommand(rawCommand);
        setType(CommandType.PARTY_ACCEPT_INVITE);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PartyService.PartyResult result = CommandServiceLocator.getInstance().getPartyService()
                .acceptInvite(context.getPlayerId(), inviterName);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (inviterName == null || inviterName.trim().isEmpty()) {
            return ValidationResult.error("邀请者名称不能为空");
        }
        return ValidationResult.success();
    }
}

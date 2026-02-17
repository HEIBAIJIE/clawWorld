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
public class PartyRejectRequestCommand extends Command {
    private String requesterName;

    @Builder
    public PartyRejectRequestCommand(String requesterName, String rawCommand) {
        this.requesterName = requesterName;
        setRawCommand(rawCommand);
        setType(CommandType.PARTY_REJECT_REQUEST);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PartyService.PartyResult result = CommandServiceLocator.getInstance().getPartyService()
                .rejectJoinRequest(context.getPlayerId(), requesterName);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (requesterName == null || requesterName.trim().isEmpty()) {
            return ValidationResult.error("请求者名称不能为空");
        }
        return ValidationResult.success();
    }
}

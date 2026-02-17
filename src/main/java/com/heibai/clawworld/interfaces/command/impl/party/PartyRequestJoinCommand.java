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
public class PartyRequestJoinCommand extends Command {
    private String playerName;

    @Builder
    public PartyRequestJoinCommand(String playerName, String rawCommand) {
        this.playerName = playerName;
        setRawCommand(rawCommand);
        setType(CommandType.PARTY_REQUEST_JOIN);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PartyService.PartyResult result = CommandServiceLocator.getInstance().getPartyService()
                .requestJoin(context.getPlayerId(), playerName);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (playerName == null || playerName.trim().isEmpty()) {
            return ValidationResult.error("玩家名称不能为空");
        }
        return ValidationResult.success();
    }
}

package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartyKickCommand extends Command {
    private String playerName;

    @Builder
    public PartyKickCommand(String playerName, String rawCommand) {
        this.playerName = playerName;
        setRawCommand(rawCommand);
        setType(CommandType.PARTY_KICK);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 PartyService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (playerName == null || playerName.trim().isEmpty()) {
            return ValidationResult.error("玩家名称不能为空");
        }
        return ValidationResult.success();
    }
}

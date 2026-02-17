package com.heibai.clawworld.interfaces.command.impl.player;

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
public class EquipCommand extends Command {
    private String itemName;

    @Builder
    public EquipCommand(String itemName, String rawCommand) {
        this.itemName = itemName;
        setRawCommand(rawCommand);
        setType(CommandType.EQUIP);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PlayerSessionService.OperationResult result = CommandServiceLocator.getInstance().getPlayerSessionService()
                .equipItem(context.getPlayerId(), itemName);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (itemName == null || itemName.trim().isEmpty()) {
            return ValidationResult.error("装备名称不能为空");
        }
        return ValidationResult.success();
    }
}

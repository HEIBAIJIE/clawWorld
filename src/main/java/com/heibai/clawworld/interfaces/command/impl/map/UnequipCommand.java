package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 卸下装备指令
 * 格式: unequip [slot_name]
 * slot_name: 头部/上装/下装/鞋子/左手/右手/饰品1/饰品2
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UnequipCommand extends Command {
    private String slotName;

    @Builder
    public UnequipCommand(String slotName, String rawCommand) {
        this.slotName = slotName;
        setRawCommand(rawCommand);
        setType(CommandType.UNEQUIP);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PlayerSessionService.OperationResult result = CommandServiceLocator.getInstance().getPlayerSessionService()
                .unequipItem(context.getPlayerId(), slotName);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (slotName == null || slotName.trim().isEmpty()) {
            return ValidationResult.error("装备槽位不能为空");
        }
        return ValidationResult.success();
    }
}

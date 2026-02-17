package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 移动指令
 * move [x] [y]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MoveCommand extends Command {
    private int targetX;
    private int targetY;

    @Builder
    public MoveCommand(int targetX, int targetY, String rawCommand) {
        this.targetX = targetX;
        this.targetY = targetY;
        setRawCommand(rawCommand);
        setType(CommandType.MOVE);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        MapEntityService.MoveResult result = CommandServiceLocator.getInstance().getMapEntityService()
                .movePlayer(context.getPlayerId(), targetX, targetY);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (targetX < 0 || targetY < 0) {
            return ValidationResult.error("坐标不能为负数");
        }
        return ValidationResult.success();
    }
}

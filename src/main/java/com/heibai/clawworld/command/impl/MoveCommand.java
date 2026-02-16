package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
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
        throw new UnsupportedOperationException("需要注入 MapEntityService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (targetX < 0 || targetY < 0) {
            return ValidationResult.error("坐标不能为负数");
        }
        return ValidationResult.success();
    }
}

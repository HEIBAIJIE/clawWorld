package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InteractCommand extends Command {
    private String targetName;
    private String option;

    @Builder
    public InteractCommand(String targetName, String option, String rawCommand) {
        this.targetName = targetName;
        this.option = option;
        setRawCommand(rawCommand);
        setType(CommandType.INTERACT);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 MapEntityService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (targetName == null || targetName.trim().isEmpty()) {
            return ValidationResult.error("目标名称不能为空");
        }
        if (option == null || option.trim().isEmpty()) {
            return ValidationResult.error("交互选项不能为空");
        }
        return ValidationResult.success();
    }
}

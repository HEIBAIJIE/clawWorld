package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
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
        MapEntityService.InteractionResult result = CommandServiceLocator.getInstance().getMapEntityService()
                .interact(context.getPlayerId(), targetName, option);

        if (result.isSuccess()) {
            if (result.isWindowChanged()) {
                return CommandResult.successWithWindowChange(
                        result.getMessage(),
                        CommandContext.WindowType.valueOf(result.getNewWindowType()),
                        "窗口已切换: " + result.getNewWindowType()
                );
            } else {
                return CommandResult.success(result.getMessage());
            }
        } else {
            return CommandResult.error(result.getMessage());
        }
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

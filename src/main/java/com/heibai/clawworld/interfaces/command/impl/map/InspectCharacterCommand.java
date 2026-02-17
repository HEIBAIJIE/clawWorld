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
 * 查看角色指令
 * inspect [character_name]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectCharacterCommand extends Command {
    private String characterName;

    @Builder
    public InspectCharacterCommand(String characterName, String rawCommand) {
        this.characterName = characterName;
        setRawCommand(rawCommand);
        setType(CommandType.INSPECT_CHARACTER);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        MapEntityService.EntityInfo info = CommandServiceLocator.getInstance().getMapEntityService()
                .inspectCharacter(context.getPlayerId(), characterName);

        if (info.isSuccess()) {
            return CommandResult.success(info.getAttributes().toString());
        } else {
            return CommandResult.error(info.getMessage());
        }
    }

    @Override
    public ValidationResult validate() {
        if (characterName == null || characterName.trim().isEmpty()) {
            return ValidationResult.error("角色名称不能为空");
        }
        return ValidationResult.success();
    }
}

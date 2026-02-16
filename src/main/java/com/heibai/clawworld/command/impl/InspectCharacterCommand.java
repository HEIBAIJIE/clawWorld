package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
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
        throw new UnsupportedOperationException("需要注入 MapEntityService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (characterName == null || characterName.trim().isEmpty()) {
            return ValidationResult.error("角色名称不能为空");
        }
        return ValidationResult.success();
    }
}

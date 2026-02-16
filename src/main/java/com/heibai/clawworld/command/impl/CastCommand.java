package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CastCommand extends Command {
    private String skillName;

    @Builder
    public CastCommand(String skillName, String rawCommand) {
        this.skillName = skillName;
        setRawCommand(rawCommand);
        setType(CommandType.CAST);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 CombatService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (skillName == null || skillName.trim().isEmpty()) {
            return ValidationResult.error("技能名称不能为空");
        }
        return ValidationResult.success();
    }
}

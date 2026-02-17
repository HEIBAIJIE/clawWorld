package com.heibai.clawworld.interfaces.command.impl.combat;

import com.heibai.clawworld.application.service.CombatService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
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
        String combatId = context.getWindowId();

        CombatService.ActionResult result = CommandServiceLocator.getInstance().getCombatService()
                .castSkill(combatId, context.getPlayerId(), skillName);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (skillName == null || skillName.trim().isEmpty()) {
            return ValidationResult.error("技能名称不能为空");
        }
        return ValidationResult.success();
    }
}

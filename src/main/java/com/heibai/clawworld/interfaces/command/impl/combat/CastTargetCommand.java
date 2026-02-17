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
public class CastTargetCommand extends Command {
    private String skillName;
    private String targetName;

    @Builder
    public CastTargetCommand(String skillName, String targetName, String rawCommand) {
        this.skillName = skillName;
        this.targetName = targetName;
        setRawCommand(rawCommand);
        setType(CommandType.CAST_TARGET);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String combatId = context.getWindowId();

        CombatService.ActionResult result = CommandServiceLocator.getInstance().getCombatService()
                .castSkillOnTarget(combatId, context.getPlayerId(), skillName, targetName);

        if (result.isSuccess()) {
            if (result.isCombatEnded()) {
                return CommandResult.successWithWindowChange(
                        result.getMessage(),
                        CommandContext.WindowType.MAP,
                        "战斗已结束，返回地图"
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
        if (skillName == null || skillName.trim().isEmpty()) {
            return ValidationResult.error("技能名称不能为空");
        }
        if (targetName == null || targetName.trim().isEmpty()) {
            return ValidationResult.error("目标名称不能为空");
        }
        return ValidationResult.success();
    }
}

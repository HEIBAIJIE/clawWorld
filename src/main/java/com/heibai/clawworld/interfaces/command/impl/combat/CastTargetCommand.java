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
                // 战斗结束时，将战利品日志作为消息的一部分返回
                // 因为战斗实例已被移除，无法从状态日志中获取
                String message = result.getMessage();
                if (result.getBattleLog() != null && !result.getBattleLog().isEmpty()) {
                    message = result.getBattleLog();
                }
                return CommandResult.successWithWindowChange(
                        message,
                        CommandContext.WindowType.MAP,
                        "战斗已结束，返回地图"
                );
            } else {
                // 战斗进行中，日志会在状态日志中单独显示
                return CommandResult.success(result.getMessage());
            }
        } else {
            // 失败时也要返回战斗日志（如果有）
            String message = result.getMessage();
            if (result.getBattleLog() != null && !result.getBattleLog().isEmpty()) {
                message = result.getBattleLog();
            }
            // 检查是否战斗已结束（玩家被击败的情况）
            if (result.isCombatEnded()) {
                return CommandResult.errorWithWindowChange(
                        message,
                        CommandContext.WindowType.MAP,
                        "战斗已结束，返回地图"
                );
            }
            return CommandResult.error(message);
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

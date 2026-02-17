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
public class WaitCombatCommand extends Command {

    @Builder
    public WaitCombatCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.WAIT_COMBAT);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String combatId = context.getWindowId();

        CombatService.ActionResult result = CommandServiceLocator.getInstance().getCombatService()
                .waitTurn(combatId, context.getPlayerId());

        if (result.isSuccess()) {
            if (result.isCombatEnded()) {
                // 战斗结束时，将战利品日志作为消息的一部分返回
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
                return CommandResult.success(result.getMessage());
            }
        } else {
            return CommandResult.error(result.getMessage());
        }
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}

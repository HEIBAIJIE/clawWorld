package com.heibai.clawworld.interfaces.command.impl.map;

import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 注册指令
 * register [role_name] [player_name]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegisterCommand extends Command {
    private String roleName;
    private String playerName;

    @Builder
    public RegisterCommand(String roleName, String playerName, String rawCommand) {
        this.roleName = roleName;
        this.playerName = playerName;
        setRawCommand(rawCommand);
        setType(CommandType.REGISTER);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        PlayerSessionService playerSessionService = CommandServiceLocator.getInstance().getPlayerSessionService();
        PlayerSessionService.SessionResult result = playerSessionService.registerPlayer(
                context.getSessionId(),
                roleName,
                playerName
        );

        if (result.isSuccess()) {
            return CommandResult.successWithWindowChange(
                    result.getMessage(),
                    CommandContext.WindowType.MAP,
                    result.getWindowContent()
            );
        } else {
            return CommandResult.error(result.getMessage());
        }
    }

    @Override
    public ValidationResult validate() {
        if (roleName == null || roleName.trim().isEmpty()) {
            return ValidationResult.error("职业名称不能为空");
        }
        if (playerName == null || playerName.trim().isEmpty()) {
            return ValidationResult.error("玩家昵称不能为空");
        }
        return ValidationResult.success();
    }
}

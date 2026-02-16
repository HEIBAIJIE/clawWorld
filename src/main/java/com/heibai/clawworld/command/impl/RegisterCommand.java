package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import com.heibai.clawworld.service.game.PlayerSessionService;
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
        // 调用玩家会话管理服务的注册接口
        // 这里只是定义接口调用，具体实现由服务层完成
        throw new UnsupportedOperationException("需要注入 PlayerSessionService 来执行此指令");
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

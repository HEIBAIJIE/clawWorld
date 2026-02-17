package com.heibai.clawworld.interfaces.command;

import org.springframework.stereotype.Component;

/**
 * 指令执行器
 * 负责验证和执行指令
 */
@Component
public class CommandExecutor {

    /**
     * 执行指令
     * @param command 指令对象
     * @param context 执行上下文
     * @return 执行结果
     */
    public CommandResult execute(Command command, CommandContext context) {
        // 先验证指令
        Command.ValidationResult validation = command.validate();
        if (!validation.isValid()) {
            return CommandResult.error(validation.getErrorMessage());
        }

        // 直接调用Command的execute方法
        try {
            return command.execute(context);
        } catch (Exception e) {
            return CommandResult.error("执行指令时发生错误: " + e.getMessage());
        }
    }
}


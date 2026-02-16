package com.heibai.clawworld.command;

import lombok.Data;

import java.util.List;

/**
 * 指令基类
 * 所有指令都继承自这个类
 */
@Data
public abstract class Command {
    /**
     * 指令类型
     */
    private CommandType type;

    /**
     * 原始指令字符串
     */
    private String rawCommand;

    /**
     * 执行指令
     * @param context 执行上下文
     * @return 执行结果
     */
    public abstract CommandResult execute(CommandContext context);

    /**
     * 验证指令参数是否合法
     * @return 验证结果，如果不合法返回错误信息
     */
    public abstract ValidationResult validate();

    /**
     * 指令类型枚举
     */
    public enum CommandType {
        // 注册窗口
        REGISTER,

        // 地图窗口
        INSPECT_SELF,
        INSPECT_CHARACTER,
        SAY,
        SAY_TO,
        INTERACT,
        MOVE,
        USE_ITEM,
        EQUIP,
        ATTRIBUTE_ADD,
        PARTY_KICK,
        PARTY_END,
        PARTY_LEAVE,
        WAIT,
        LEAVE,

        // 战斗窗口
        CAST,
        CAST_TARGET,
        USE_ITEM_COMBAT,
        WAIT_COMBAT,
        END_COMBAT,

        // 交易窗口
        TRADE_ADD,
        TRADE_REMOVE,
        TRADE_MONEY,
        TRADE_LOCK,
        TRADE_UNLOCK,
        TRADE_CONFIRM,
        TRADE_END
    }

    /**
     * 验证结果
     */
    @Data
    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;

        public static ValidationResult success() {
            ValidationResult result = new ValidationResult();
            result.setValid(true);
            return result;
        }

        public static ValidationResult error(String message) {
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setErrorMessage(message);
            return result;
        }
    }
}

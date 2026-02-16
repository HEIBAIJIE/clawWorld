package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TradeMoneyCommand extends Command {
    private int amount;

    @Builder
    public TradeMoneyCommand(int amount, String rawCommand) {
        this.amount = amount;
        setRawCommand(rawCommand);
        setType(CommandType.TRADE_MONEY);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 TradeService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (amount < 0) {
            return ValidationResult.error("金额不能为负数");
        }
        return ValidationResult.success();
    }
}

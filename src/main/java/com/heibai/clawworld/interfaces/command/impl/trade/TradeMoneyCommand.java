package com.heibai.clawworld.interfaces.command.impl.trade;

import com.heibai.clawworld.application.service.TradeService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
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
        String tradeId = context.getWindowId();

        TradeService.OperationResult result = CommandServiceLocator.getInstance().getTradeService()
                .setMoney(tradeId, context.getPlayerId(), amount);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (amount < 0) {
            return ValidationResult.error("金额不能为负数");
        }
        return ValidationResult.success();
    }
}

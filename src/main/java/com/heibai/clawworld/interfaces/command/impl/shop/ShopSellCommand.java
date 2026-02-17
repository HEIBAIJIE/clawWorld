package com.heibai.clawworld.interfaces.command.impl.shop;

import com.heibai.clawworld.application.service.ShopService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ShopSellCommand extends Command {
    private String itemName;
    private int quantity;

    @Builder
    public ShopSellCommand(String itemName, int quantity, String rawCommand) {
        this.itemName = itemName;
        this.quantity = quantity;
        setRawCommand(rawCommand);
        setType(CommandType.SHOP_SELL);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String shopId = context.getWindowId().replace("shop_", "");
        ShopService.OperationResult result = CommandServiceLocator.getInstance().getShopService()
                .sellItem(context.getPlayerId(), shopId, itemName, quantity);

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (itemName == null || itemName.trim().isEmpty()) {
            return ValidationResult.error("物品名称不能为空");
        }
        if (quantity <= 0) {
            return ValidationResult.error("出售数量必须大于0");
        }
        return ValidationResult.success();
    }
}

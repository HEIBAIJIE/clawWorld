package com.heibai.clawworld.infrastructure.config.data.item;

import lombok.Data;

/**
 * 物品配置 - 从CSV读取的扁平化配置
 */
@Data
public class ItemConfig {
    private String id;
    private String name;
    private String description;
    private String type;
    private int maxStack;
    private int basePrice;
    private String effect;
    private Integer effectValue;

    // GUI资源
    private String icon;
}

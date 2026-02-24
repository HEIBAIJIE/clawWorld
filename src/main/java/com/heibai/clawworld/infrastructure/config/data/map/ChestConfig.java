package com.heibai.clawworld.infrastructure.config.data.map;

import lombok.Data;

/**
 * 宝箱配置 - 从CSV读取
 */
@Data
public class ChestConfig {
    private String id;
    private String name;
    private String description;
    private String type;  // SMALL: 小宝箱(个人), LARGE: 大宝箱(服务器)
    private int respawnSeconds;  // 刷新时间（仅大宝箱有效）
}

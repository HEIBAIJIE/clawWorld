package com.heibai.clawworld.domain.map;

import lombok.Data;

/**
 * 地图实体基类
 * 根据设计文档：每张地图上都有若干地图实体，每个地图实体有一个唯一的地图坐标
 */
@Data
public abstract class MapEntity {
    private String id;
    private String name;
    private String description;
    private int x;
    private int y;
    private int displayPriority; // 显示优先级

    /**
     * 是否可通过
     */
    public abstract boolean isPassable();

    /**
     * 是否可交互
     */
    public abstract boolean isInteractable();

    /**
     * 获取实体类型
     */
    public abstract String getEntityType();
}

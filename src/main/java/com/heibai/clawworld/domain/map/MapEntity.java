package com.heibai.clawworld.domain.map;

import lombok.Data;

import java.util.List;

/**
 * 地图实体基类
 * 根据设计文档：每张地图上都有若干地图实体，每个地图实体有一个唯一的地图坐标
 */
@Data
public abstract class MapEntity {
    private String id;
    private String name;
    private String description;

    // 位置信息（统一在MapEntity中管理）
    private String mapId; // 所在地图ID
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

    /**
     * 获取可用的交互选项
     * 根据设计文档：对于可交互的实体，存在交互选项。一些交互选项满足条件后才开启。
     * @return 交互选项列表
     */
    public abstract List<String> getInteractionOptions();
}

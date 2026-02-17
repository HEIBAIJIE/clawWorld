package com.heibai.clawworld.domain.map;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.List;

/**
 * 篝火实体
 * 根据设计文档：篝火是一种特殊的地图实体，可交互，可通过
 * 支持【休息】交互：交互时回满生命和法力
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Campfire extends MapEntity {
    private String id;
    private String name;
    private String description;

    @Override
    public boolean isPassable() {
        return true;
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public String getEntityType() {
        return "CAMPFIRE";
    }

    @Override
    public List<String> getInteractionOptions() {
        return Arrays.asList("休息");
    }
}

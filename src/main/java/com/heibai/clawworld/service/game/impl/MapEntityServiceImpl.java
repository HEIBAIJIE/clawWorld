package com.heibai.clawworld.service.game.impl;

import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.service.game.MapEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 地图实体管理服务实现（Stub）
 */
@Service
@RequiredArgsConstructor
public class MapEntityServiceImpl implements MapEntityService {

    @Override
    public EntityInfo inspectCharacter(String playerId, String characterName) {
        // TODO: 实现查看角色逻辑
        return EntityInfo.success(characterName, "Character", Collections.emptyMap());
    }

    @Override
    public MoveResult movePlayer(String playerId, int targetX, int targetY) {
        // TODO: 实现移动逻辑
        return MoveResult.success(targetX, targetY, "移动到 (" + targetX + "," + targetY + ")");
    }

    @Override
    public InteractionResult interact(String playerId, String targetName, String option) {
        // TODO: 实现交互逻辑
        return InteractionResult.success("与 " + targetName + " 交互: " + option);
    }

    @Override
    public List<MapEntity> getNearbyInteractableEntities(String playerId) {
        // TODO: 实现获取周围实体逻辑
        return Collections.emptyList();
    }

    @Override
    public List<MapEntity> getMapEntities(String mapId) {
        // TODO: 实现获取地图实体逻辑
        return Collections.emptyList();
    }
}

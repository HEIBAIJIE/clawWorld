package com.heibai.clawworld.service;

import com.heibai.clawworld.domain.character.Enemy;
import com.heibai.clawworld.domain.character.Npc;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.domain.map.Waypoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MapInitializationServiceTest {

    private static final Logger log = LoggerFactory.getLogger(MapInitializationServiceTest.class);

    @Autowired
    private MapInitializationService mapInitializationService;

    @Test
    void testMapInitialization() {
        log.info("=== 地图初始化测试 ===");

        // 验证地图已加载
        var allMaps = mapInitializationService.getAllMaps();
        assertFalse(allMaps.isEmpty(), "应该至少有一张地图");

        log.info("已加载地图数量: {}", allMaps.size());
        for (GameMap map : allMaps) {
            log.info("地图: {} ({}x{}), 安全: {}, 推荐等级: {}, 实体数: {}",
                    map.getName(), map.getWidth(), map.getHeight(),
                    map.isSafe(), map.getRecommendedLevel(), map.getEntities().size());
        }
    }
}

package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.PathfindingService;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.persistence.repository.EnemyInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 寻路服务实现
 */
@Service
@RequiredArgsConstructor
public class PathfindingServiceImpl implements PathfindingService {

    private final ConfigDataManager configDataManager;
    private final EnemyInstanceRepository enemyInstanceRepository;

    // 8方向移动
    private static final int[][] DIRECTIONS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0},          {1, 0},
            {-1, 1},  {0, 1},  {1, 1}
    };

    @Override
    public List<int[]> findPath(String mapId, int startX, int startY, int targetX, int targetY) {
        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return null;
        }

        // 检查目标位置是否在地图范围内
        if (targetX < 0 || targetX >= mapConfig.getWidth() || targetY < 0 || targetY >= mapConfig.getHeight()) {
            return null;
        }

        // 检查目标位置是否可通行
        if (!isPositionPassableInternal(mapConfig, targetX, targetY)) {
            return null;
        }

        // A*算法节点
        class Node implements Comparable<Node> {
            int x, y;
            int g; // 从起点到当前节点的实际代价
            int h; // 从当前节点到终点的估计代价（启发式）
            Node parent;

            Node(int x, int y, int g, int h, Node parent) {
                this.x = x;
                this.y = y;
                this.g = g;
                this.h = h;
                this.parent = parent;
            }

            int f() {
                return g + h;
            }

            @Override
            public int compareTo(Node other) {
                return Integer.compare(this.f(), other.f());
            }
        }

        // 计算启发式距离（切比雪夫距离，因为支持8方向移动）
        java.util.function.BiFunction<Integer, Integer, Integer> heuristic = (x, y) ->
                Math.max(Math.abs(x - targetX), Math.abs(y - targetY));

        // 开放列表（待探索）
        PriorityQueue<Node> openList = new PriorityQueue<>();
        // 已访问集合
        Set<String> closedSet = new HashSet<>();
        // 用于快速查找开放列表中的节点
        Map<String, Node> openMap = new HashMap<>();

        // 起始节点
        Node startNode = new Node(startX, startY, 0, heuristic.apply(startX, startY), null);
        openList.offer(startNode);
        openMap.put(startX + "," + startY, startNode);

        while (!openList.isEmpty()) {
            Node current = openList.poll();
            String currentKey = current.x + "," + current.y;
            openMap.remove(currentKey);

            // 到达目标
            if (current.x == targetX && current.y == targetY) {
                // 回溯构建路径
                List<int[]> path = new ArrayList<>();
                Node node = current;
                while (node.parent != null) {
                    path.add(0, new int[]{node.x, node.y});
                    node = node.parent;
                }
                return path;
            }

            closedSet.add(currentKey);

            // 探索相邻节点
            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];
                String neighborKey = nx + "," + ny;

                // 检查边界
                if (nx < 0 || nx >= mapConfig.getWidth() || ny < 0 || ny >= mapConfig.getHeight()) {
                    continue;
                }

                // 检查是否已访问
                if (closedSet.contains(neighborKey)) {
                    continue;
                }

                // 检查是否可通行
                if (!isPositionPassableInternal(mapConfig, nx, ny)) {
                    continue;
                }

                // 计算移动代价（对角线移动代价稍高，用14表示√2*10，直线用10）
                int moveCost = (dir[0] != 0 && dir[1] != 0) ? 14 : 10;
                int newG = current.g + moveCost;

                Node existingNode = openMap.get(neighborKey);
                if (existingNode != null) {
                    // 如果新路径更短，更新节点
                    if (newG < existingNode.g) {
                        openList.remove(existingNode);
                        existingNode.g = newG;
                        existingNode.parent = current;
                        openList.offer(existingNode);
                    }
                } else {
                    // 添加新节点
                    Node newNode = new Node(nx, ny, newG, heuristic.apply(nx, ny), current);
                    openList.offer(newNode);
                    openMap.put(neighborKey, newNode);
                }
            }
        }

        // 无法到达
        return null;
    }

    @Override
    public Set<String> calculateReachabilityMap(String mapId, int startX, int startY) {
        Set<String> reachable = new HashSet<>();

        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return reachable;
        }

        // BFS从起始位置开始
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{startX, startY});
        reachable.add(startX + "," + startY);

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int cx = current[0];
            int cy = current[1];

            for (int[] dir : DIRECTIONS) {
                int nx = cx + dir[0];
                int ny = cy + dir[1];
                String key = nx + "," + ny;

                // 检查边界
                if (nx < 0 || nx >= mapConfig.getWidth() || ny < 0 || ny >= mapConfig.getHeight()) {
                    continue;
                }

                // 检查是否已访问
                if (reachable.contains(key)) {
                    continue;
                }

                // 检查是否可通行
                if (isPositionPassableInternal(mapConfig, nx, ny)) {
                    reachable.add(key);
                    queue.offer(new int[]{nx, ny});
                }
            }
        }

        return reachable;
    }

    @Override
    public boolean isPositionPassable(String mapId, int x, int y) {
        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return false;
        }
        return isPositionPassableInternal(mapConfig, x, y);
    }

    /**
     * 内部方法：检查位置是否可通过
     */
    private boolean isPositionPassableInternal(MapConfig mapConfig, int x, int y) {
        // 检查坐标是否在地图范围内
        if (x < 0 || y < 0 || x >= mapConfig.getWidth() || y >= mapConfig.getHeight()) {
            return false;
        }

        // 获取该位置的地形配置
        List<String> terrainTypes = configDataManager.getMapTerrain(mapConfig.getId(), x, y);

        // 检查是否有不可通过的地形
        for (String terrain : terrainTypes) {
            var terrainConfig = configDataManager.getTerrainType(terrain);
            if (terrainConfig != null && !terrainConfig.isPassable()) {
                return false;
            }
        }

        // 检查该位置是否有存活的敌人
        var enemies = enemyInstanceRepository.findByMapId(mapConfig.getId());
        for (var enemy : enemies) {
            if (enemy.getX() == x && enemy.getY() == y && !enemy.isDead()) {
                return false;
            }
        }

        return true;
    }
}

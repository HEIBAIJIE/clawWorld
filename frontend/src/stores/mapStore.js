import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useMapStore = defineStore('map', () => {
  // 地图基础信息
  const id = ref('')
  const name = ref('')
  const description = ref('')
  const width = ref(10)
  const height = ref(10)
  const isSafe = ref(true)
  const recommendedLevel = ref(0)
  const defaultTerrain = ref('GRASS')

  // 特殊地形矩形列表
  const specialTerrains = ref([])

  // 地图网格数据 - 二维数组 [y][x]
  const grid = ref([])

  // 实体列表
  const entities = ref([])

  // 当前窗口类型
  const windowType = ref('map') // 'map' | 'combat' | 'trade' | 'shop' | 'register'

  // 计算属性：按类型分组的实体
  const entitiesByType = computed(() => {
    const grouped = {
      PLAYER: [],
      ENEMY: [],
      ENEMY_ELITE: [],
      ENEMY_BOSS: [],
      ENEMY_WORLD_BOSS: [],
      NPC: [],
      WAYPOINT: [],
      CAMPFIRE: [],
      CHEST_SMALL: [],
      CHEST_LARGE: []
    }
    entities.value.forEach(entity => {
      if (grouped[entity.type]) {
        grouped[entity.type].push(entity)
      } else if (entity.type && entity.type.startsWith('ENEMY')) {
        // 所有敌人类型都归入 ENEMY
        grouped.ENEMY.push(entity)
      } else if (entity.type && entity.type.startsWith('CHEST')) {
        // 宝箱类型
        if (entity.type === 'CHEST_SMALL') {
          grouped.CHEST_SMALL.push(entity)
        } else {
          grouped.CHEST_LARGE.push(entity)
        }
      }
    })
    return grouped
  })

  // 获取指定位置的实体（返回优先级最高的）
  function getEntityAt(x, y) {
    const entitiesAtPos = entities.value.filter(e => e.x === x && e.y === y)
    if (entitiesAtPos.length === 0) return null
    if (entitiesAtPos.length === 1) return entitiesAtPos[0]

    // 按优先级排序，返回最高优先级的实体
    return entitiesAtPos.sort((a, b) => getEntityDisplayPriority(b) - getEntityDisplayPriority(a))[0]
  }

  // 获取指定位置的所有实体
  function getEntitiesAt(x, y) {
    return entities.value.filter(e => e.x === x && e.y === y)
  }

  /**
   * 获取实体的显示优先级
   * 优先级：当前玩家(100) > 传送点(90) > 敌人(80) > 其他玩家(70) > 篝火(60) > 宝箱(55) > NPC(50) > 其他实体(10)
   */
  function getEntityDisplayPriority(entity) {
    const type = entity.type || entity.entityType
    if (!type) return 10

    // 当前玩家优先级最高（通过 isCurrentPlayer 标记或其他方式判断）
    if (entity.isCurrentPlayer) return 100

    switch (type.toUpperCase()) {
      case 'WAYPOINT': return 90
      case 'ENEMY':
      case 'ENEMY_ELITE':
      case 'ENEMY_BOSS':
      case 'ENEMY_WORLD_BOSS': return 80
      case 'PLAYER': return 70
      case 'CAMPFIRE': return 60
      case 'CHEST_SMALL':
      case 'CHEST_LARGE': return 55
      case 'NPC': return 50
      default: return 10
    }
  }

  // 检查位置是否可通行
  function isPassable(x, y) {
    if (x < 0 || x >= width.value || y < 0 || y >= height.value) {
      return false
    }
    // 检查地形
    const cell = grid.value[y]?.[x]
    if (cell && !cell.passable) {
      return false
    }
    // 检查是否有不可通行的实体（如敌人）
    const entitiesAtPos = getEntitiesAt(x, y)
    for (const entity of entitiesAtPos) {
      if (entity.type === 'ENEMY' && !entity.isDead) {
        return false
      }
    }
    return true
  }

  // 计算两点间距离
  function getDistance(x1, y1, x2, y2) {
    return Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))
  }

  // 更新地图信息
  function updateMapInfo(data) {
    console.log('[MapStore] 更新地图信息:', data)
    if (data.id !== undefined) id.value = data.id
    if (data.name !== undefined) name.value = data.name
    if (data.description !== undefined) description.value = data.description
    if (data.width !== undefined) width.value = data.width
    if (data.height !== undefined) height.value = data.height
    if (data.isSafe !== undefined) isSafe.value = data.isSafe
    if (data.recommendedLevel !== undefined) recommendedLevel.value = data.recommendedLevel
    if (data.defaultTerrain !== undefined) defaultTerrain.value = data.defaultTerrain
  }

  // 更新特殊地形
  function updateSpecialTerrains(terrains) {
    console.log('[MapStore] 更新特殊地形:', terrains.length, '个矩形')
    specialTerrains.value = terrains
  }

  // 更新地图网格
  function updateGrid(newGrid) {
    console.log('[MapStore] 更新地图网格:', newGrid.length, 'x', newGrid[0]?.length || 0)
    grid.value = newGrid
  }

  // 更新实体列表
  function updateEntities(newEntities) {
    console.log('[MapStore] 更新实体列表:', newEntities.length, '个实体')
    entities.value = newEntities
  }

  // 更新单个实体
  function updateEntity(entityName, updates) {
    const entity = entities.value.find(e => e.name === entityName)
    if (entity) {
      console.log('[MapStore] 更新实体:', entityName, updates)
      Object.assign(entity, updates)
    }
  }

  // 添加实体
  function addEntity(entity) {
    const existing = entities.value.find(e => e.name === entity.name)
    if (!existing) {
      console.log('[MapStore] 添加实体:', entity.name, entity.type)
      entities.value.push(entity)
    }
  }

  // 移除实体
  function removeEntity(entityName) {
    const index = entities.value.findIndex(e => e.name === entityName)
    if (index !== -1) {
      console.log('[MapStore] 移除实体:', entityName)
      entities.value.splice(index, 1)
    }
  }

  // 设置窗口类型
  function setWindowType(type) {
    console.log('[MapStore] 设置窗口类型:', type)
    windowType.value = type
  }

  // 重置状态
  function reset() {
    id.value = ''
    name.value = ''
    width.value = 10
    height.value = 10
    defaultTerrain.value = 'GRASS'
    specialTerrains.value = []
    grid.value = []
    entities.value = []
    windowType.value = 'map'
  }

  return {
    // 状态
    id, name, description, width, height, isSafe, recommendedLevel,
    defaultTerrain, specialTerrains,
    grid, entities, windowType,
    // 计算属性
    entitiesByType,
    // 方法
    getEntityAt, getEntitiesAt, isPassable, getDistance,
    updateMapInfo, updateSpecialTerrains, updateGrid, updateEntities, updateEntity,
    addEntity, removeEntity,
    setWindowType, reset
  }
})

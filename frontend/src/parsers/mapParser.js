/**
 * 地图数据解析器
 * 解析特殊地形、实体列表等信息
 */

/**
 * 解析特殊地形（矩形区域格式）
 * 输入格式: "岩石（不可通过） 矩形范围(0,0)~(0,2)"
 * @param {string} content - 特殊地形文本
 * @returns {array} 特殊地形矩形数组
 */
export function parseSpecialTerrain(content) {
  const terrains = []
  const lines = content.split('\n')
  const pattern = /^(.+?)（(可通过|不可通过)）\s*矩形范围\((\d+),(\d+)\)~\((\d+),(\d+)\)/

  for (const line of lines) {
    const match = line.trim().match(pattern)
    if (match) {
      terrains.push({
        name: match[1].trim(),
        passable: match[2] === '可通过',
        x1: parseInt(match[3]),
        y1: parseInt(match[4]),
        x2: parseInt(match[5]),
        y2: parseInt(match[6])
      })
    }
  }

  return terrains
}

/**
 * 根据特殊地形和默认地形构建地图网格
 * @param {number} width - 地图宽度
 * @param {number} height - 地图高度
 * @param {string} defaultTerrain - 默认地形
 * @param {array} specialTerrains - 特殊地形矩形数组
 * @returns {object} { grid: 二维数组, width, height }
 */
export function buildMapGrid(width, height, defaultTerrain, specialTerrains) {
  // 构建二维数组 [y][x]
  const grid = []

  for (let y = 0; y < height; y++) {
    grid[y] = []
    for (let x = 0; x < width; x++) {
      grid[y][x] = {
        x,
        y,
        terrain: defaultTerrain,
        passable: isPassableTerrain(defaultTerrain),
        isEntity: false
      }
    }
  }

  // 应用特殊地形
  for (const terrain of specialTerrains) {
    const minX = Math.min(terrain.x1, terrain.x2)
    const maxX = Math.max(terrain.x1, terrain.x2)
    const minY = Math.min(terrain.y1, terrain.y2)
    const maxY = Math.max(terrain.y1, terrain.y2)

    for (let y = minY; y <= maxY && y < height; y++) {
      for (let x = minX; x <= maxX && x < width; x++) {
        if (grid[y] && grid[y][x]) {
          grid[y][x].terrain = terrain.name
          grid[y][x].passable = terrain.passable
        }
      }
    }
  }

  return { grid, width, height }
}

/**
 * 判断地形是否可通行
 */
function isPassableTerrain(terrain) {
  const impassable = ['WATER', 'ROCK', 'TREE', 'WALL', '水', '岩石', '树', '墙', '河流', '海洋', '山脉', '浅水']
  return !impassable.includes(terrain?.toUpperCase?.()) && !impassable.includes(terrain)
}

/**
 * 解析实体列表
 * 输入格式:
 * "史莱姆#1 Lv.3 (8,8) [可直接交互] [类型：普通敌人] [交互选项: 查看, 攻击]"
 * "哥布林#1 Lv.2 (5,5) [已死亡，30秒后刷新] [类型：普通敌人] [交互选项: 查看]"
 * "森林入口 (0,0) [可直接交互] [类型：传送点] [交互选项: 传送到新手村·村中心传送点]"
 * @param {string} content - 实体列表文本
 * @returns {array} 实体数组
 */
export function parseEntityList(content) {
  const lines = content.split('\n')
  const entities = []

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.includes('地图实体：') || trimmed.includes('没有其他实体')) continue

    // 尝试匹配带等级的格式: "名称 Lv.X (x,y) [状态] [类型：XXX] [交互选项: ...]"
    let match = trimmed.match(/^(.+?)\s+Lv\.(\d+)\s+\((\d+),(\d+)\)\s+\[([^\]]+)\]\s+\[类型[：:]([^\]]+)\](?:\s+\[交互选项[：:]\s*([^\]]*)\])?/)

    if (match) {
      const accessibility = match[5]
      const entity = {
        name: match[1].trim(),
        level: parseInt(match[2]),
        x: parseInt(match[3]),
        y: parseInt(match[4]),
        accessibility: accessibility,
        type: mapEntityTypeToInternal(match[6].trim()),
        displayType: match[6].trim(),
        interactionOptions: parseInteractionOptions(match[7] || ''),
        isInRange: accessibility === '可直接交互',
        isDead: false,
        respawnSeconds: 0
      }

      // 检查是否是死亡状态
      const deadMatch = accessibility.match(/已死亡，(\d+)秒后刷新/)
      if (deadMatch) {
        entity.isDead = true
        entity.respawnSeconds = parseInt(deadMatch[1])
        entity.isInRange = false
      }

      entities.push(entity)
      continue
    }

    // 尝试匹配不带等级的格式: "名称 (x,y) [状态] [类型：XXX] [交互选项: ...]"
    match = trimmed.match(/^(.+?)\s+\((\d+),(\d+)\)\s+\[([^\]]+)\]\s+\[类型[：:]([^\]]+)\](?:\s+\[交互选项[：:]\s*([^\]]*)\])?/)

    if (match) {
      const accessibility = match[4]
      const entity = {
        name: match[1].trim(),
        level: null,
        x: parseInt(match[2]),
        y: parseInt(match[3]),
        accessibility: accessibility,
        type: mapEntityTypeToInternal(match[5].trim()),
        displayType: match[5].trim(),
        interactionOptions: parseInteractionOptions(match[6] || ''),
        isInRange: accessibility === '可直接交互',
        isDead: false,
        respawnSeconds: 0
      }

      // 检查是否是死亡状态
      const deadMatch = accessibility.match(/已死亡，(\d+)秒后刷新/)
      if (deadMatch) {
        entity.isDead = true
        entity.respawnSeconds = parseInt(deadMatch[1])
        entity.isInRange = false
      }

      entities.push(entity)
    }
  }

  return entities
}

/**
 * 将中文实体类型映射为内部类型
 */
function mapEntityTypeToInternal(displayType) {
  const typeMap = {
    '玩家': 'PLAYER',
    'NPC': 'NPC',
    '传送点': 'WAYPOINT',
    '篝火': 'CAMPFIRE',
    '普通敌人': 'ENEMY',
    '精英敌人': 'ENEMY_ELITE',
    '地图BOSS': 'ENEMY_BOSS',
    '世界BOSS': 'ENEMY_WORLD_BOSS',
    // 兼容旧格式
    'PLAYER': 'PLAYER',
    'ENEMY': 'ENEMY',
    'WAYPOINT': 'WAYPOINT',
    'CAMPFIRE': 'CAMPFIRE'
  }
  return typeMap[displayType] || displayType
}

/**
 * 解析交互选项
 */
function parseInteractionOptions(optionsStr) {
  if (!optionsStr || optionsStr.trim() === '') return []
  return optionsStr.split(',').map(s => s.trim()).filter(Boolean)
}

/**
 * 解析地图信息头
 * 输入格式: "当前地图：森林入口（10×10），通往黑暗森林的入口【危险区域】推荐等级: 3，默认地形：草地"
 * @param {string} content - 地图信息文本
 * @returns {object} 地图信息
 */
export function parseMapInfo(content) {
  const result = {
    name: '',
    description: '',
    isSafe: true,
    recommendedLevel: 0,
    width: 10,
    height: 10,
    defaultTerrain: 'GRASS'
  }

  // 解析地图名和尺寸
  const nameMatch = content.match(/当前地图[：:]\s*([^（(]+)[（(](\d+)[×x](\d+)[）)]/)
  if (nameMatch) {
    result.name = nameMatch[1].trim()
    result.width = parseInt(nameMatch[2])
    result.height = parseInt(nameMatch[3])
  } else {
    // 兼容旧格式
    const oldNameMatch = content.match(/当前地图名[：:]\s*([^，,【]+)/)
    if (oldNameMatch) {
      result.name = oldNameMatch[1].trim()
    }
  }

  // 解析描述
  const descMatch = content.match(/[）)][，,]([^【]+)/)
  if (descMatch) {
    result.description = descMatch[1].trim()
  }

  // 判断是否安全区
  result.isSafe = !content.includes('危险区域') && !content.includes('战斗区')

  // 解析推荐等级
  const levelMatch = content.match(/推荐等级[：:]\s*(\d+)/)
  if (levelMatch) {
    result.recommendedLevel = parseInt(levelMatch[1])
  }

  // 解析默认地形
  const terrainMatch = content.match(/默认地形[：:]\s*(\S+)/)
  if (terrainMatch) {
    result.defaultTerrain = terrainMatch[1].trim()
  }

  return result
}

/**
 * 解析可移动交互的实体
 * 输入格式: "哥布林#1: 移动到 (6,5) 可交互"
 * @param {string} content - 文本
 * @returns {array} 可移动交互信息
 */
export function parseMoveToInteract(content) {
  const lines = content.split('\n')
  const result = []
  const pattern = /^(.+?)[：:]\s*移动到\s*\((\d+),(\d+)\)\s*可交互/

  for (const line of lines) {
    const match = line.trim().match(pattern)
    if (match) {
      result.push({
        name: match[1].trim(),
        moveToX: parseInt(match[2]),
        moveToY: parseInt(match[3])
      })
    }
  }

  return result
}

export default {
  parseSpecialTerrain,
  buildMapGrid,
  parseEntityList,
  parseMapInfo,
  parseMoveToInteract
}

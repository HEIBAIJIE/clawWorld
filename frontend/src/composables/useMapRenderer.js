import { ref, watch, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/playerStore'
import { useMapStore } from '../stores/mapStore'

/**
 * 地图渲染的composable
 */
export function useMapRenderer(canvasRef) {
  const playerStore = usePlayerStore()
  const mapStore = useMapStore()

  // 渲染配置
  const CELL_SIZE = ref(48)
  const MIN_CELL_SIZE = 24
  const MAX_CELL_SIZE = 72

  // 视口偏移
  const offsetX = ref(0)
  const offsetY = ref(0)

  // 屏幕居中偏移（当地图比视口小时）
  const screenOffsetX = ref(0)
  const screenOffsetY = ref(0)

  // 鼠标悬浮的格子
  const hoveredCell = ref(null)

  // 拖动状态
  const isDragging = ref(false)
  const dragStartX = ref(0)
  const dragStartY = ref(0)
  const dragOffsetX = ref(0)  // 像素级别的拖动偏移
  const dragOffsetY = ref(0)

  // 颜色配置
  const TERRAIN_COLORS = {
    GRASS: '#2d5a27',
    WATER: '#1a4a6e',
    ROCK: '#4a4a4a',
    SAND: '#c2b280',
    SNOW: '#e8e8e8',
    TREE: '#1b4d1b',
    WALL: '#333333',
    SHALLOW_WATER: '#3a7a9e',
    MOUNTAIN: '#5a5a5a',
    RIVER: '#1a5a8e',
    OCEAN: '#0a3a5e',
    STONE: '#5a5a5a',
    '草地': '#2d5a27',
    '水': '#1a4a6e',
    '岩石': '#4a4a4a',
    '沙地': '#c2b280',
    '雪地': '#e8e8e8',
    '树': '#1b4d1b',
    '墙': '#333333',
    '浅水': '#3a7a9e',
    '山脉': '#5a5a5a',
    '河流': '#1a5a8e',
    '海洋': '#0a3a5e',
    '石头地': '#5a5a5a'
  }

  const ENTITY_COLORS = {
    PLAYER: '#4CAF50',
    ENEMY: '#f44336',
    ENEMY_ELITE: '#ff5722',
    ENEMY_BOSS: '#e91e63',
    ENEMY_WORLD_BOSS: '#9c27b0',
    NPC: '#2196F3',
    WAYPOINT: '#9c27b0',
    CAMPFIRE: '#ff9800',
    CHEST_SMALL: '#8B4513',
    CHEST_LARGE: '#DAA520'
  }

  const ENTITY_ICONS = {
    PLAYER: '👤',
    ENEMY: '👹',
    ENEMY_ELITE: '💀',
    ENEMY_BOSS: '👿',
    ENEMY_WORLD_BOSS: '🐉',
    NPC: '🧙',
    WAYPOINT: '🌀',
    CAMPFIRE: '🔥',
    CHEST_SMALL: '📦',
    CHEST_LARGE: '🎁'
  }

  /**
   * 渲染地图
   */
  function render() {
    const canvas = canvasRef.value
    if (!canvas) return

    const ctx = canvas.getContext('2d')
    const { width, height } = canvas

    // 清空画布
    ctx.fillStyle = '#0a0a0a'
    ctx.fillRect(0, 0, width, height)

    // 计算视口格子数（+2 确保边缘完整显示）
    const viewportWidth = Math.ceil(width / CELL_SIZE.value) + 2
    const viewportHeight = Math.ceil(height / CELL_SIZE.value) + 2

    // 居中玩家并计算屏幕偏移
    centerOnPlayer(viewportWidth, viewportHeight)

    // 计算屏幕居中偏移（当地图比视口小时）
    screenOffsetX.value = mapStore.width < viewportWidth
      ? Math.floor((viewportWidth - mapStore.width) / 2)
      : 0
    screenOffsetY.value = mapStore.height < viewportHeight
      ? Math.floor((viewportHeight - mapStore.height) / 2)
      : 0

    // 应用拖动偏移（保存当前状态）
    ctx.save()
    ctx.translate(dragOffsetX.value, dragOffsetY.value)

    // 渲染地形
    renderTerrain(ctx, viewportWidth, viewportHeight)

    // 渲染网格线
    renderGrid(ctx, viewportWidth, viewportHeight)

    // 渲染实体
    renderEntities(ctx)

    // 渲染玩家
    renderPlayer(ctx)

    // 渲染悬浮高亮
    if (hoveredCell.value) {
      renderHoveredCell(ctx)
    }

    // 恢复状态
    ctx.restore()
  }

  /**
   * 居中玩家
   */
  function centerOnPlayer(viewportWidth, viewportHeight) {
    // 如果地图比视口小，居中显示整个地图
    // 注意：这里计算的是地图坐标的起始偏移，不是屏幕偏移
    if (mapStore.width <= viewportWidth) {
      // 地图比视口小，从0开始显示整个地图
      offsetX.value = 0
    } else {
      const targetOffsetX = playerStore.x - Math.floor(viewportWidth / 2)
      offsetX.value = Math.max(0, Math.min(targetOffsetX, mapStore.width - viewportWidth))
    }

    if (mapStore.height <= viewportHeight) {
      // 地图比视口小，从0开始显示整个地图
      offsetY.value = 0
    } else {
      const targetOffsetY = playerStore.y - Math.floor(viewportHeight / 2)
      offsetY.value = Math.max(0, Math.min(targetOffsetY, mapStore.height - viewportHeight))
    }
  }

  /**
   * 渲染地形
   */
  function renderTerrain(ctx, viewportWidth, viewportHeight) {
    // 直接遍历地图的所有格子
    for (let mapY = 0; mapY < mapStore.height; mapY++) {
      for (let mapX = 0; mapX < mapStore.width; mapX++) {
        // 计算屏幕坐标
        const vx = mapX - offsetX.value + screenOffsetX.value
        // Y轴翻转：地图高Y值显示在屏幕上方
        const vy = (mapStore.height - 1 - mapY) - offsetY.value + screenOffsetY.value

        // 跳过视口外的格子
        if (vx < -1 || vx > viewportWidth || vy < -1 || vy > viewportHeight) {
          continue
        }

        const cell = mapStore.grid[mapY]?.[mapX]
        const terrain = cell?.terrain || 'GRASS'
        const color = TERRAIN_COLORS[terrain] || TERRAIN_COLORS.GRASS

        const screenX = vx * CELL_SIZE.value
        const screenY = vy * CELL_SIZE.value

        ctx.fillStyle = color
        ctx.fillRect(screenX, screenY, CELL_SIZE.value, CELL_SIZE.value)
      }
    }
  }

  /**
   * 渲染网格线
   */
  function renderGrid(ctx, viewportWidth, viewportHeight) {
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)'
    ctx.lineWidth = 1

    // 垂直线
    for (let x = 0; x <= viewportWidth; x++) {
      ctx.beginPath()
      ctx.moveTo(x * CELL_SIZE.value, 0)
      ctx.lineTo(x * CELL_SIZE.value, viewportHeight * CELL_SIZE.value)
      ctx.stroke()
    }

    // 水平线
    for (let y = 0; y <= viewportHeight; y++) {
      ctx.beginPath()
      ctx.moveTo(0, y * CELL_SIZE.value)
      ctx.lineTo(viewportWidth * CELL_SIZE.value, y * CELL_SIZE.value)
      ctx.stroke()
    }
  }

  /**
   * 渲染实体
   * 每个格子只渲染优先级最高的实体
   */
  function renderEntities(ctx) {
    const canvas = canvasRef.value

    // 按位置分组，每个位置只保留优先级最高的实体
    const entityByPosition = new Map()
    for (const entity of mapStore.entities) {
      // 跳过玩家自己（玩家单独渲染）
      if (entity.name === playerStore.name) continue

      const key = `${entity.x},${entity.y}`
      const existing = entityByPosition.get(key)
      if (!existing || getEntityRenderPriority(entity) > getEntityRenderPriority(existing)) {
        entityByPosition.set(key, entity)
      }
    }

    // 渲染每个位置优先级最高的实体
    for (const entity of entityByPosition.values()) {
      // 计算屏幕坐标（使用与地形相同的公式）
      const vx = entity.x - offsetX.value + screenOffsetX.value
      const vy = (mapStore.height - 1 - entity.y) - offsetY.value + screenOffsetY.value
      const screenX = vx * CELL_SIZE.value + CELL_SIZE.value / 2
      const screenY = vy * CELL_SIZE.value + CELL_SIZE.value / 2

      // 检查是否在视口内
      if (screenX < -CELL_SIZE.value || screenX > canvas.width + CELL_SIZE.value ||
          screenY < -CELL_SIZE.value || screenY > canvas.height + CELL_SIZE.value) {
        continue
      }

      // 检查敌人是否死亡或宝箱是否已开启，设置透明度
      const isDead = entity.isDead === true
      const isChestOpened = entity.isOpened === true
      if (isDead || isChestOpened) {
        ctx.globalAlpha = 0.4
      }

      // 绘制实体圆形背景
      const color = ENTITY_COLORS[entity.type] || '#888'
      ctx.fillStyle = color
      ctx.beginPath()
      ctx.arc(screenX, screenY, CELL_SIZE.value * 0.35, 0, Math.PI * 2)
      ctx.fill()

      // 绘制图标
      const icon = ENTITY_ICONS[entity.type] || '?'
      ctx.font = `${CELL_SIZE.value * 0.4}px Arial`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(icon, screenX, screenY)

      // 绘制名称
      ctx.fillStyle = '#fff'
      ctx.font = `${Math.max(10, CELL_SIZE.value * 0.22)}px Arial`
      ctx.fillText(entity.name, screenX, screenY + CELL_SIZE.value * 0.45)

      // 绘制等级（如果有）
      if (entity.level) {
        ctx.fillStyle = '#ffd700'
        ctx.font = `${Math.max(8, CELL_SIZE.value * 0.18)}px Arial`
        ctx.fillText(`Lv.${entity.level}`, screenX, screenY - CELL_SIZE.value * 0.45)
      }

      // 恢复透明度
      if (isDead || isChestOpened) {
        ctx.globalAlpha = 1.0
      }
    }
  }

  /**
   * 获取实体的渲染优先级
   * 优先级：传送点(90) > 敌人(80) > 其他玩家(70) > 篝火(60) > 宝箱(55) > NPC(50) > 其他实体(10)
   */
  function getEntityRenderPriority(entity) {
    const type = entity.type
    if (!type) return 10

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

  /**
   * 渲染玩家
   */
  function renderPlayer(ctx) {
    // 计算屏幕坐标（使用与地形相同的公式）
    const vx = playerStore.x - offsetX.value + screenOffsetX.value
    const vy = (mapStore.height - 1 - playerStore.y) - offsetY.value + screenOffsetY.value
    const screenX = vx * CELL_SIZE.value + CELL_SIZE.value / 2
    const screenY = vy * CELL_SIZE.value + CELL_SIZE.value / 2

    // 绘制玩家光环
    ctx.strokeStyle = '#8BC34A'
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.arc(screenX, screenY, CELL_SIZE.value * 0.4, 0, Math.PI * 2)
    ctx.stroke()

    // 绘制玩家圆形背景
    ctx.fillStyle = '#4CAF50'
    ctx.beginPath()
    ctx.arc(screenX, screenY, CELL_SIZE.value * 0.35, 0, Math.PI * 2)
    ctx.fill()

    // 绘制玩家图标
    ctx.font = `${CELL_SIZE.value * 0.4}px Arial`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(playerStore.roleIcon, screenX, screenY)

    // 绘制朝向指示器
    const facingX = screenX + playerStore.facing.dx * CELL_SIZE.value * 0.5
    const facingY = screenY - playerStore.facing.dy * CELL_SIZE.value * 0.5 // Y轴翻转
    ctx.fillStyle = 'rgba(139, 195, 74, 0.5)'
    ctx.beginPath()
    ctx.arc(facingX, facingY, CELL_SIZE.value * 0.1, 0, Math.PI * 2)
    ctx.fill()

    // 绘制名称
    ctx.fillStyle = '#8BC34A'
    ctx.font = `bold ${Math.max(10, CELL_SIZE.value * 0.22)}px Arial`
    ctx.fillText(playerStore.name || '你', screenX, screenY + CELL_SIZE.value * 0.45)
  }

  /**
   * 渲染悬浮高亮
   */
  function renderHoveredCell(ctx) {
    const { x, y } = hoveredCell.value
    // 计算屏幕坐标（使用与地形相同的公式）
    const vx = x - offsetX.value + screenOffsetX.value
    const vy = (mapStore.height - 1 - y) - offsetY.value + screenOffsetY.value
    const screenX = vx * CELL_SIZE.value
    const screenY = vy * CELL_SIZE.value

    ctx.strokeStyle = 'rgba(76, 175, 80, 0.8)'
    ctx.lineWidth = 2
    ctx.strokeRect(screenX + 2, screenY + 2, CELL_SIZE.value - 4, CELL_SIZE.value - 4)
  }

  /**
   * 屏幕坐标转地图坐标
   */
  function screenToMap(screenX, screenY) {
    const canvas = canvasRef.value
    if (!canvas) return null

    // 考虑拖动偏移后的屏幕坐标
    const adjustedScreenX = screenX - dragOffsetX.value
    const adjustedScreenY = screenY - dragOffsetY.value

    // 屏幕格子坐标
    const vx = Math.floor(adjustedScreenX / CELL_SIZE.value)
    const vy = Math.floor(adjustedScreenY / CELL_SIZE.value)

    // 反向计算地图坐标
    // vx = mapX - offsetX + screenOffsetX => mapX = vx + offsetX - screenOffsetX
    // vy = (mapStore.height - 1 - mapY) - offsetY + screenOffsetY
    // => mapY = mapStore.height - 1 - (vy + offsetY - screenOffsetY)
    const mapX = vx + offsetX.value - screenOffsetX.value
    const mapY = mapStore.height - 1 - (vy + offsetY.value - screenOffsetY.value)

    return { x: mapX, y: mapY }
  }

  /**
   * 处理鼠标移动
   */
  function handleMouseMove(event) {
    const canvas = canvasRef.value
    if (!canvas) return

    const rect = canvas.getBoundingClientRect()
    const screenX = event.clientX - rect.left
    const screenY = event.clientY - rect.top

    hoveredCell.value = screenToMap(screenX, screenY)
    render()
  }

  /**
   * 处理鼠标离开
   */
  function handleMouseLeave() {
    hoveredCell.value = null
    render()
  }

  /**
   * 处理缩放
   */
  function handleWheel(event) {
    event.preventDefault()
    const delta = event.deltaY > 0 ? -4 : 4
    CELL_SIZE.value = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, CELL_SIZE.value + delta))
    render()
  }

  /**
   * 处理鼠标按下（开始拖动）
   */
  function handleMouseDown(event) {
    // 只响应左键
    if (event.button !== 0) return

    isDragging.value = true
    dragStartX.value = event.clientX
    dragStartY.value = event.clientY

    // 添加全局事件监听
    document.addEventListener('mousemove', handleDragMove)
    document.addEventListener('mouseup', handleMouseUp)
  }

  /**
   * 处理拖动移动
   */
  function handleDragMove(event) {
    if (!isDragging.value) return

    const deltaX = event.clientX - dragStartX.value
    const deltaY = event.clientY - dragStartY.value

    // 直接使用像素偏移
    const tempDragOffsetX = dragOffsetX.value + deltaX
    const tempDragOffsetY = dragOffsetY.value + deltaY

    // 限制拖动范围（像素级别，约10格）
    const maxDrag = 10 * CELL_SIZE.value
    dragOffsetX.value = Math.max(-maxDrag, Math.min(maxDrag, tempDragOffsetX))
    dragOffsetY.value = Math.max(-maxDrag, Math.min(maxDrag, tempDragOffsetY))

    // 更新起始点
    dragStartX.value = event.clientX
    dragStartY.value = event.clientY

    render()
  }

  /**
   * 处理鼠标松开（结束拖动）
   */
  function handleMouseUp() {
    isDragging.value = false
    document.removeEventListener('mousemove', handleDragMove)
    document.removeEventListener('mouseup', handleMouseUp)
  }

  /**
   * 重置拖动偏移（回到玩家中心）
   */
  function resetDragOffset() {
    dragOffsetX.value = 0
    dragOffsetY.value = 0
    render()
  }

  /**
   * 调整画布大小
   */
  function resizeCanvas() {
    const canvas = canvasRef.value
    if (!canvas) return

    const parent = canvas.parentElement
    if (parent) {
      canvas.width = parent.clientWidth
      canvas.height = parent.clientHeight
      render()
    }
  }

  // 监听数据变化重新渲染
  watch(
    [
      () => mapStore.grid,
      () => mapStore.entities,
      () => mapStore.width,
      () => mapStore.height,
      () => playerStore.x,
      () => playerStore.y
    ],
    () => render(),
    { deep: true }
  )

  // 生命周期
  onMounted(() => {
    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resizeCanvas)
  })

  return {
    CELL_SIZE,
    offsetX,
    offsetY,
    hoveredCell,
    isDragging,
    dragOffsetX,
    dragOffsetY,
    render,
    screenToMap,
    handleMouseMove,
    handleMouseLeave,
    handleWheel,
    handleMouseDown,
    handleMouseUp,
    resetDragOffset,
    resizeCanvas
  }
}

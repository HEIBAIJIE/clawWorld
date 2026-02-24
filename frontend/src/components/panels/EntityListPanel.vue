<template>
  <div class="popup-panel sci-panel entity-list-panel">
    <div class="popup-panel-header">
      <span class="popup-panel-title">地图实体</span>
      <button class="popup-panel-close" @click="uiStore.closePanel()">×</button>
    </div>

    <div class="popup-panel-content sci-scrollbar">
      <!-- 敌人 -->
      <div v-if="enemies.length > 0" class="entity-category">
        <div class="category-header">
          <span class="category-icon">👹</span>
          <span class="category-title">敌人</span>
          <span class="category-count">({{ enemies.length }})</span>
        </div>
        <div
          v-for="entity in enemies"
          :key="entity.name"
          class="entity-item"
          :class="{ 'entity-dead': entity.isDead }"
          @click="handleEntityClick(entity)"
        >
          <div class="entity-icon enemy" :class="{ 'icon-dead': entity.isDead }">👹</div>
          <div class="entity-details">
            <div class="entity-name">
              {{ entity.name }}
              <span v-if="entity.level" class="entity-level">Lv.{{ entity.level }}</span>
            </div>
            <div class="entity-meta">
              <span v-if="entity.isDead" class="entity-dead-status">
                已死亡，{{ entity.respawnSeconds }}秒后刷新
              </span>
              <span v-else class="entity-distance" :class="{ 'in-range': entity.isInRange }">
                {{ entity.isInRange ? '可交互' : `距离 ${entity.distance}` }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 玩家 -->
      <div v-if="players.length > 0" class="entity-category">
        <div class="category-header">
          <span class="category-icon">👤</span>
          <span class="category-title">玩家</span>
          <span class="category-count">({{ players.length }})</span>
        </div>
        <div
          v-for="entity in players"
          :key="entity.name"
          class="entity-item"
          @click="handleEntityClick(entity)"
        >
          <div class="entity-icon player">👤</div>
          <div class="entity-details">
            <div class="entity-name">{{ entity.name }}</div>
            <div class="entity-meta">
              <span class="entity-distance" :class="{ 'in-range': entity.isInRange }">
                {{ entity.isInRange ? '可交互' : `距离 ${entity.distance}` }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- NPC -->
      <div v-if="npcs.length > 0" class="entity-category">
        <div class="category-header">
          <span class="category-icon">🧙</span>
          <span class="category-title">NPC</span>
          <span class="category-count">({{ npcs.length }})</span>
        </div>
        <div
          v-for="entity in npcs"
          :key="entity.name"
          class="entity-item"
          @click="handleEntityClick(entity)"
        >
          <div class="entity-icon npc">🧙</div>
          <div class="entity-details">
            <div class="entity-name">{{ entity.name }}</div>
            <div class="entity-meta">
              <span class="entity-distance" :class="{ 'in-range': entity.isInRange }">
                {{ entity.isInRange ? '可交互' : `距离 ${entity.distance}` }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 传送点 -->
      <div v-if="waypoints.length > 0" class="entity-category">
        <div class="category-header">
          <span class="category-icon">🌀</span>
          <span class="category-title">传送点</span>
          <span class="category-count">({{ waypoints.length }})</span>
        </div>
        <div
          v-for="entity in waypoints"
          :key="entity.name"
          class="entity-item"
          @click="handleEntityClick(entity)"
        >
          <div class="entity-icon waypoint">🌀</div>
          <div class="entity-details">
            <div class="entity-name">{{ entity.name }}</div>
            <div class="entity-meta">
              <span class="entity-distance" :class="{ 'in-range': entity.isInRange }">
                {{ entity.isInRange ? '可交互' : `距离 ${entity.distance}` }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 宝箱 -->
      <div v-if="chests.length > 0" class="entity-category">
        <div class="category-header">
          <span class="category-icon">📦</span>
          <span class="category-title">宝箱</span>
          <span class="category-count">({{ chests.length }})</span>
        </div>
        <div
          v-for="entity in chests"
          :key="entity.name"
          class="entity-item"
          :class="{ 'entity-opened': entity.isOpened }"
          @click="handleEntityClick(entity)"
        >
          <div class="entity-icon chest" :class="{ 'icon-opened': entity.isOpened }">
            {{ entity.type === 'CHEST_LARGE' ? '🎁' : '📦' }}
          </div>
          <div class="entity-details">
            <div class="entity-name">
              {{ entity.name }}
              <span v-if="entity.type === 'CHEST_LARGE'" class="chest-type">[大]</span>
            </div>
            <div class="entity-meta">
              <span v-if="entity.isOpened && entity.remainingRespawnSeconds > 0" class="entity-respawn">
                {{ entity.remainingRespawnSeconds }}秒后刷新
              </span>
              <span v-else class="entity-distance" :class="{ 'in-range': entity.isInRange }">
                {{ entity.isInRange ? '可交互' : `距离 ${entity.distance}` }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="totalCount === 0" class="empty-list">
        当前地图没有实体
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUIStore } from '../../stores/uiStore'
import { useMapStore } from '../../stores/mapStore'
import { usePlayerStore } from '../../stores/playerStore'

const uiStore = useUIStore()
const mapStore = useMapStore()
const playerStore = usePlayerStore()

// 按类型分组
const enemies = computed(() => {
  // 合并所有敌人类型
  const allEnemies = [
    ...mapStore.entitiesByType.ENEMY,
    ...mapStore.entitiesByType.ENEMY_ELITE,
    ...mapStore.entitiesByType.ENEMY_BOSS,
    ...mapStore.entitiesByType.ENEMY_WORLD_BOSS
  ]
  return allEnemies.sort((a, b) => a.distance - b.distance)
})

const players = computed(() =>
  mapStore.entitiesByType.PLAYER
    .filter(e => e.name !== playerStore.name)
    .sort((a, b) => a.distance - b.distance)
)

const npcs = computed(() =>
  mapStore.entitiesByType.NPC.sort((a, b) => a.distance - b.distance)
)

const waypoints = computed(() =>
  mapStore.entitiesByType.WAYPOINT.sort((a, b) => a.distance - b.distance)
)

const chests = computed(() => {
  const allChests = [
    ...mapStore.entitiesByType.CHEST_SMALL,
    ...mapStore.entitiesByType.CHEST_LARGE
  ]
  return allChests.sort((a, b) => a.distance - b.distance)
})

const totalCount = computed(() =>
  enemies.value.length + players.value.length + npcs.value.length + waypoints.value.length + chests.value.length
)

// 点击实体
function handleEntityClick(entity) {
  if (entity.isInRange && entity.interactionOptions?.length > 0) {
    uiStore.openInteraction(entity)
  } else if (!entity.isInRange) {
    uiStore.showToast(`需要移动到 (${entity.moveToX || entity.x}, ${entity.moveToY || entity.y}) 才能交互`, 'info')
  }
}
</script>

<style scoped>
.entity-level {
  color: #ffd700;
  font-size: 11px;
  margin-left: 4px;
}

.empty-list {
  text-align: center;
  color: var(--text-muted);
  padding: 24px;
  font-size: 13px;
}

.entity-dead {
  opacity: 0.5;
}

.icon-dead {
  filter: grayscale(100%);
}

.entity-dead-status {
  color: #888;
  font-size: 11px;
}

.entity-opened {
  opacity: 0.6;
}

.icon-opened {
  filter: grayscale(80%);
}

.chest-type {
  color: #ffd700;
  font-size: 10px;
  margin-left: 4px;
}

.entity-respawn {
  color: #888;
  font-size: 11px;
}
</style>

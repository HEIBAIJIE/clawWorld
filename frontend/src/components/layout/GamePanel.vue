<template>
  <div class="game-panel">
    <!-- 地图子标题栏 -->
    <div class="map-header" v-if="mapStore.name">
      <span class="map-name">{{ mapStore.name }}</span>
      <span class="map-type" :class="{ safe: mapStore.isSafe, danger: !mapStore.isSafe }">
        {{ mapStore.isSafe ? '安全区' : '危险区' }}
      </span>
      <span class="map-level" v-if="!mapStore.isSafe && mapStore.recommendedLevel > 0">
        推荐等级: {{ mapStore.recommendedLevel }}
      </span>
    </div>

    <!-- 地图视图 -->
    <div class="map-container">
      <MapView />

      <!-- 战斗窗口（覆盖在地图上） -->
      <CombatWindow v-if="mapStore.windowType === 'combat' || combatStore.showResult" />
    </div>

    <!-- 功能按钮栏 -->
    <ActionBar />

    <!-- 弹出面板 -->
    <CharacterPanel v-if="uiStore.activePanel === 'character'" />
    <InventoryPanel v-if="uiStore.activePanel === 'inventory'" />
    <PartyPanel v-if="uiStore.activePanel === 'party'" />
    <EntityListPanel v-if="uiStore.activePanel === 'entities'" />
  </div>
</template>

<script setup>
import { useUIStore } from '../../stores/uiStore'
import { useMapStore } from '../../stores/mapStore'
import { useCombatStore } from '../../stores/combatStore'
import MapView from '../map/MapView.vue'
import ActionBar from '../panels/ActionBar.vue'
import CharacterPanel from '../panels/CharacterPanel.vue'
import InventoryPanel from '../panels/InventoryPanel.vue'
import PartyPanel from '../panels/PartyPanel.vue'
import EntityListPanel from '../panels/EntityListPanel.vue'
import CombatWindow from '../combat/CombatWindow.vue'

const uiStore = useUIStore()
const mapStore = useMapStore()
const combatStore = useCombatStore()
</script>

<style scoped>
.game-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.map-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius) var(--panel-radius) 0 0;
  margin-bottom: -1px;
}

.map-name {
  color: var(--text-highlight);
  font-weight: 500;
  font-size: 14px;
}

.map-type {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 2px;
}

.map-type.safe {
  background: rgba(76, 175, 80, 0.2);
  color: var(--primary);
}

.map-type.danger {
  background: rgba(244, 67, 54, 0.2);
  color: var(--entity-enemy);
}

.map-level {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: auto;
}

.map-container {
  flex: 1;
  position: relative;
  overflow: hidden;
}
</style>

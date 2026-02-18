<template>
  <div class="inventory-panel-overlay" @click.self="$emit('close')">
    <div class="inventory-panel">
      <div class="panel-header">
        <span class="panel-title">战斗背包</span>
        <button class="close-btn" @click="$emit('close')">×</button>
      </div>

      <div class="item-list">
        <div
          v-for="item in usableItems"
          :key="item.name"
          class="item-row"
          @click="handleUseItem(item)"
        >
          <span class="item-name">{{ item.name }}</span>
          <span class="item-count">x{{ item.count }}</span>
        </div>

        <div v-if="usableItems.length === 0" class="no-items">
          没有可在战斗中使用的物品
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { usePlayerStore } from '../../stores/playerStore'
import { useCommand } from '../../composables/useCommand'

const emit = defineEmits(['close'])

const playerStore = usePlayerStore()
const { sendCommand } = useCommand()

// 过滤出可在战斗中使用的物品（药剂等）
const usableItems = computed(() => {
  return playerStore.inventory.filter(item => {
    // 简单判断：名称包含"药"或"剂"的物品可以在战斗中使用
    return item.name.includes('药') || item.name.includes('剂')
  })
})

function handleUseItem(item) {
  sendCommand(`use ${item.name}`)
  emit('close')
}
</script>

<style scoped>
.inventory-panel-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.inventory-panel {
  width: 280px;
  max-height: 350px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid var(--border-color);
}

.panel-title {
  font-size: 13px;
  color: var(--text-primary);
}

.close-btn {
  width: 24px;
  height: 24px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: var(--text-primary);
}

.item-list {
  max-height: 300px;
  overflow-y: auto;
}

.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background 0.2s ease;
}

.item-row:hover {
  background: rgba(255, 193, 7, 0.1);
}

.item-name {
  font-size: 12px;
  color: var(--text-primary);
}

.item-count {
  font-size: 11px;
  color: var(--text-muted);
}

.no-items {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
</style>

<template>
  <div class="popup-panel sci-panel inventory-panel">
    <div class="popup-panel-header">
      <span class="popup-panel-title">背包 ({{ playerStore.inventory.length }}/50)</span>
      <button class="popup-panel-close" @click="uiStore.closePanel()">×</button>
    </div>

    <div class="popup-panel-content inventory-content">
      <div class="inventory-grid">
        <div
          v-for="(slot, index) in inventorySlots"
          :key="index"
          class="inventory-slot"
          :class="{ empty: !slot }"
          @click="handleSlotClick(slot, $event)"
          @contextmenu.prevent="handleSlotRightClick(slot, $event)"
        >
          <template v-if="slot">
            <span class="item-icon">{{ getItemIcon(slot) }}</span>
            <span v-if="slot.quantity > 1" class="item-count">{{ slot.quantity }}</span>
          </template>
        </div>
      </div>

      <!-- 金币显示放在最下面 -->
      <div class="gold-display">
        <span class="gold-icon">💰</span>
        <span class="gold-value">{{ playerStore.gold }}</span>
        <span class="gold-label">金币</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUIStore } from '../../stores/uiStore'
import { usePlayerStore } from '../../stores/playerStore'
import { useCommand } from '../../composables/useCommand'

const uiStore = useUIStore()
const playerStore = usePlayerStore()
const { useItem, equip, sendCommand } = useCommand()

// 48格背包槽位
const inventorySlots = computed(() => {
  const slots = new Array(48).fill(null)
  playerStore.inventory.forEach((item, index) => {
    if (index < 48) {
      slots[index] = item
    }
  })
  return slots
})

// 获取物品图标
function getItemIcon(item) {
  if (!item) return ''
  if (item.isEquipment) {
    // 根据槽位显示不同图标
    const slotIcons = {
      '头部': '🪖',
      '上装': '👕',
      '下装': '👖',
      '鞋子': '👟',
      '左手': '🛡️',
      '右手': '⚔️',
      '饰品1': '💍',
      '饰品2': '📿'
    }
    return slotIcons[item.slotName] || '⚔️'
  }
  if (item.name.includes('药水') || item.name.includes('药剂')) return '🧪'
  if (item.name.includes('技能书')) return '📖'
  if (item.name.includes('礼包')) return '🎁'
  return '📦'
}

// 获取用于命令的物品名称
function getCommandName(item) {
  if (!item) return ''
  // 装备使用displayName（不含槽位前缀）
  if (item.isEquipment && item.displayName) {
    return item.displayName
  }
  return item.name
}

// 点击槽位
function handleSlotClick(slot, event) {
  if (!slot) return
  // 单击显示物品信息
  sendCommand(`inspect ${getCommandName(slot)}`)
}

// 右键槽位
function handleSlotRightClick(slot, event) {
  if (!slot) return

  const commandName = getCommandName(slot)
  const items = [
    { label: '查看', action: () => sendCommand(`inspect ${commandName}`) }
  ]

  if (slot.isEquipment) {
    items.push({ label: '装备', action: () => equip(commandName) })
  } else {
    items.push({ label: '使用', action: () => useItem(commandName) })
  }

  uiStore.showContextMenu(event.clientX, event.clientY, items, slot)
}
</script>

<style scoped>
.inventory-content {
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.inventory-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
  flex-shrink: 0;
}

.inventory-slot {
  aspect-ratio: 1;
  background: var(--bg-dark);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  transition: all var(--transition-fast);
  min-height: 36px;
}

.inventory-slot:hover:not(.empty) {
  border-color: var(--primary);
  background: var(--bg-hover);
}

.inventory-slot.empty {
  cursor: default;
  opacity: 0.5;
}

.item-icon {
  font-size: 18px;
}

.item-count {
  position: absolute;
  bottom: 2px;
  right: 4px;
  font-size: 10px;
  color: var(--text-primary);
  text-shadow: 0 0 2px var(--bg-dark);
}

.gold-display {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-dark);
  border-radius: var(--button-radius);
  margin-top: 12px;
  flex-shrink: 0;
}

.gold-icon {
  font-size: 16px;
}

.gold-value {
  color: #ffd700;
  font-weight: 600;
  font-size: 14px;
}

.gold-label {
  color: var(--text-muted);
  font-size: 12px;
}
</style>

<template>
  <teleport to="body">
    <template v-if="uiStore.showInspectItemModal && uiStore.inspectItem">
      <!-- 遮罩 -->
      <div class="modal-overlay" @click="uiStore.closeInspectItem()"></div>

      <!-- 弹窗 -->
      <div class="inspect-modal sci-panel">
        <div class="inspect-modal-header">
          <span class="inspect-modal-title">查看物品</span>
          <button class="close-btn" @click="uiStore.closeInspectItem()">×</button>
        </div>

        <div class="inspect-modal-content">
          <!-- 物品名称 -->
          <div class="item-header">
            <span class="item-name" :class="itemRarityClass">{{ item.name }}</span>
            <span class="item-type" v-if="item.type">{{ item.type }}</span>
          </div>

          <!-- 物品属性 -->
          <div class="item-stats" v-if="hasStats">
            <div class="stat-row" v-for="(value, key) in item.stats" :key="key">
              <span class="stat-label">{{ statLabels[key] || key }}</span>
              <span class="stat-value" :class="{ positive: value > 0 }">
                {{ value > 0 ? '+' : '' }}{{ value }}
              </span>
            </div>
          </div>

          <!-- 物品描述 -->
          <div class="item-description" v-if="item.description">
            {{ item.description }}
          </div>

          <!-- 装备要求 -->
          <div class="item-requirements" v-if="item.requirements">
            <div class="section-title">装备要求</div>
            <div class="requirement" v-for="(value, key) in item.requirements" :key="key">
              {{ requirementLabels[key] || key }}: {{ value }}
            </div>
          </div>

          <!-- 原始文本 -->
          <div class="raw-text" v-if="item.rawText">
            <div class="section-title">详细信息</div>
            <pre>{{ item.rawText }}</pre>
          </div>
        </div>
      </div>
    </template>
  </teleport>
</template>

<script setup>
import { computed } from 'vue'
import { useUIStore } from '../../stores/uiStore'

const uiStore = useUIStore()

const item = computed(() => uiStore.inspectItem || {})

const statLabels = {
  physicalAttack: '物理攻击',
  physicalDefense: '物理防御',
  magicAttack: '魔法攻击',
  magicDefense: '魔法防御',
  health: '生命值',
  mana: '法力值',
  strength: '力量',
  agility: '敏捷',
  intelligence: '智力',
  vitality: '体力',
  critRate: '暴击率',
  critDamage: '暴击伤害',
  hitRate: '命中率',
  dodgeRate: '闪避率',
  speed: '速度'
}

const requirementLabels = {
  level: '等级',
  strength: '力量',
  agility: '敏捷',
  intelligence: '智力',
  vitality: '体力'
}

const hasStats = computed(() => {
  return item.value.stats && Object.keys(item.value.stats).length > 0
})

const itemRarityClass = computed(() => {
  const rarity = item.value.rarity?.toLowerCase()
  return rarity || 'common'
})
</script>

<style scoped>
.inspect-modal {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 320px;
  max-height: 80vh;
  z-index: 1001;
  display: flex;
  flex-direction: column;
}

.inspect-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.inspect-modal-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--primary-color);
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 20px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:hover {
  color: var(--text-primary);
}

.inspect-modal-content {
  padding: 16px;
  overflow-y: auto;
}

.item-header {
  margin-bottom: 12px;
}

.item-name {
  font-size: 16px;
  font-weight: 500;
  display: block;
}

.item-name.common {
  color: var(--text-primary);
}

.item-name.uncommon {
  color: #4CAF50;
}

.item-name.rare {
  color: #2196F3;
}

.item-name.epic {
  color: #9c27b0;
}

.item-name.legendary {
  color: #ff9800;
}

.item-name.mythic {
  color: #e91e63;
  text-shadow: 0 0 8px rgba(233, 30, 99, 0.5);
}

.item-type {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  display: block;
}

.item-stats {
  margin-bottom: 12px;
  padding: 8px;
  background: var(--bg-secondary);
  border-radius: 4px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 2px 0;
}

.stat-label {
  color: var(--text-secondary);
}

.stat-value {
  color: var(--text-primary);
}

.stat-value.positive {
  color: #4CAF50;
}

.item-description {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 12px;
  padding: 8px;
  background: var(--bg-secondary);
  border-radius: 4px;
  font-style: italic;
}

.item-requirements {
  margin-bottom: 12px;
}

.section-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
  text-transform: uppercase;
}

.requirement {
  font-size: 12px;
  color: var(--text-secondary);
  padding: 2px 0;
}

.raw-text pre {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--bg-secondary);
  padding: 8px;
  border-radius: 4px;
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
}
</style>

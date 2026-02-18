<template>
  <teleport to="body">
    <template v-if="uiStore.showInspectCharacterModal && uiStore.inspectCharacter">
      <!-- 遮罩 -->
      <div class="modal-overlay" @click="uiStore.closeInspectCharacter()"></div>

      <!-- 弹窗 -->
      <div class="inspect-modal sci-panel">
        <div class="inspect-modal-header">
          <span class="inspect-modal-title">查看角色</span>
          <button class="close-btn" @click="uiStore.closeInspectCharacter()">×</button>
        </div>

        <div class="inspect-modal-content">
          <!-- 基础信息 -->
          <div class="inspect-section">
            <div class="character-basic">
              <span class="character-name">{{ character.name }}</span>
              <span class="character-role">({{ character.roleName }})</span>
              <span class="character-level">Lv.{{ character.level }}</span>
            </div>
            <div class="character-position" v-if="character.x !== undefined">
              位置: ({{ character.x }}, {{ character.y }})
            </div>
          </div>

          <!-- 属性 -->
          <div class="inspect-section" v-if="character.strength !== undefined">
            <div class="section-title">属性</div>
            <div class="attributes-grid">
              <div class="attr-item">
                <span class="attr-label">力量</span>
                <span class="attr-value">{{ character.strength }}</span>
              </div>
              <div class="attr-item">
                <span class="attr-label">敏捷</span>
                <span class="attr-value">{{ character.agility }}</span>
              </div>
              <div class="attr-item">
                <span class="attr-label">智力</span>
                <span class="attr-value">{{ character.intelligence }}</span>
              </div>
              <div class="attr-item">
                <span class="attr-label">体力</span>
                <span class="attr-value">{{ character.vitality }}</span>
              </div>
            </div>
          </div>

          <!-- 战斗属性 -->
          <div class="inspect-section" v-if="character.currentHealth !== undefined">
            <div class="section-title">战斗属性</div>
            <div class="combat-stats">
              <div class="stat-row">
                <span>生命</span>
                <span>{{ character.currentHealth }}/{{ character.maxHealth }}</span>
              </div>
              <div class="stat-row">
                <span>法力</span>
                <span>{{ character.currentMana }}/{{ character.maxMana }}</span>
              </div>
              <div class="stat-row" v-if="character.physicalAttack !== undefined">
                <span>物攻</span>
                <span>{{ character.physicalAttack }}</span>
              </div>
              <div class="stat-row" v-if="character.physicalDefense !== undefined">
                <span>物防</span>
                <span>{{ character.physicalDefense }}</span>
              </div>
              <div class="stat-row" v-if="character.magicAttack !== undefined">
                <span>法攻</span>
                <span>{{ character.magicAttack }}</span>
              </div>
              <div class="stat-row" v-if="character.magicDefense !== undefined">
                <span>法防</span>
                <span>{{ character.magicDefense }}</span>
              </div>
              <div class="stat-row" v-if="character.speed !== undefined">
                <span>速度</span>
                <span>{{ character.speed }}</span>
              </div>
            </div>
          </div>

          <!-- 装备 -->
          <div class="inspect-section" v-if="hasEquipment">
            <div class="section-title">装备</div>
            <div class="equipment-list">
              <div class="equip-item" v-for="(item, slot) in character.equipment" :key="slot">
                <span class="equip-slot">{{ slotNames[slot] }}</span>
                <span class="equip-name" :class="{ empty: !item }">{{ item?.name || '无' }}</span>
              </div>
            </div>
          </div>

          <!-- 原始文本 -->
          <div class="inspect-section raw-text" v-if="character.rawText">
            <div class="section-title">详细信息</div>
            <pre>{{ character.rawText }}</pre>
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

const character = computed(() => uiStore.inspectCharacter || {})

const slotNames = {
  HEAD: '头部',
  CHEST: '上装',
  LEGS: '下装',
  FEET: '鞋子',
  LEFT_HAND: '左手',
  RIGHT_HAND: '右手',
  ACCESSORY1: '饰品1',
  ACCESSORY2: '饰品2'
}

const hasEquipment = computed(() => {
  return character.value.equipment && Object.keys(character.value.equipment).length > 0
})
</script>

<style scoped>
.inspect-modal {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 360px;
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

.inspect-section {
  margin-bottom: 16px;
}

.inspect-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
  text-transform: uppercase;
}

.character-basic {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.character-name {
  font-size: 18px;
  font-weight: 500;
  color: var(--primary-color);
}

.character-role {
  font-size: 14px;
  color: var(--text-secondary);
}

.character-level {
  font-size: 14px;
  color: #ffd700;
}

.character-position {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.attributes-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.attr-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: var(--bg-secondary);
  border-radius: 4px;
}

.attr-label {
  font-size: 11px;
  color: var(--text-muted);
}

.attr-value {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.combat-stats {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 8px;
  background: var(--bg-secondary);
  border-radius: 4px;
}

.stat-row span:first-child {
  color: var(--text-secondary);
}

.stat-row span:last-child {
  color: var(--text-primary);
}

.equipment-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.equip-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 8px;
  background: var(--bg-secondary);
  border-radius: 4px;
}

.equip-slot {
  color: var(--text-muted);
}

.equip-name {
  color: var(--text-primary);
}

.equip-name.empty {
  color: var(--text-muted);
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

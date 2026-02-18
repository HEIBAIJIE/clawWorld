<template>
  <div class="result-modal-overlay">
    <div class="result-modal" :class="{ victory: isVictory, defeat: !isVictory }">
      <div class="result-header">
        <span class="result-icon">{{ isVictory ? '🏆' : '💀' }}</span>
        <span class="result-title">{{ isVictory ? '战斗胜利' : '战斗失败' }}</span>
      </div>

      <div class="result-content" v-if="combatStore.combatResult">
        <div class="reward-section" v-if="isVictory">
          <div class="reward-item" v-if="combatStore.combatResult.experience > 0">
            <span class="reward-label">获得经验</span>
            <span class="reward-value exp">+{{ combatStore.combatResult.experience }}</span>
          </div>
          <div class="reward-item" v-if="combatStore.combatResult.gold > 0">
            <span class="reward-label">获得金币</span>
            <span class="reward-value gold">+{{ combatStore.combatResult.gold }}</span>
          </div>
          <div class="reward-item" v-if="combatStore.combatResult.items?.length > 0">
            <span class="reward-label">获得物品</span>
            <div class="reward-items">
              <span v-for="item in combatStore.combatResult.items" :key="item" class="item-tag">
                {{ item }}
              </span>
            </div>
          </div>
        </div>

        <div class="defeat-section" v-else>
          <p class="defeat-text">你被击败了...</p>
          <p class="defeat-hint" v-if="combatStore.combatResult.goldLost > 0">
            损失金币: {{ combatStore.combatResult.goldLost }}
          </p>
        </div>
      </div>

      <div class="result-footer">
        <button class="confirm-btn" @click="handleConfirm">确认</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useCombatStore } from '../../stores/combatStore'
import { useMapStore } from '../../stores/mapStore'

const combatStore = useCombatStore()
const mapStore = useMapStore()

const isVictory = computed(() => {
  return combatStore.combatResult?.victory ?? false
})

function handleConfirm() {
  combatStore.closeCombatResult()
  combatStore.reset()
  // 战斗结束后切换到地图窗口
  mapStore.setWindowType('map')
}
</script>

<style scoped>
.result-modal-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}

.result-modal {
  width: 320px;
  background: var(--bg-panel);
  border: 2px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  animation: modal-pop 0.3s ease-out;
}

@keyframes modal-pop {
  0% {
    opacity: 0;
    transform: scale(0.8);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.result-modal.victory {
  border-color: #ffc107;
  box-shadow: 0 0 30px rgba(255, 193, 7, 0.3);
}

.result-modal.defeat {
  border-color: #f44336;
  box-shadow: 0 0 30px rgba(244, 67, 54, 0.3);
}

.result-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px 16px;
  background: rgba(0, 0, 0, 0.3);
}

.result-icon {
  font-size: 48px;
  margin-bottom: 8px;
}

.result-title {
  font-size: 20px;
  font-weight: bold;
}

.victory .result-title {
  color: #ffc107;
}

.defeat .result-title {
  color: #f44336;
}

.result-content {
  padding: 20px;
}

.reward-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reward-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reward-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.reward-value {
  font-size: 16px;
  font-weight: bold;
}

.reward-value.exp {
  color: #9c27b0;
}

.reward-value.gold {
  color: #ffc107;
}

.reward-items {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.item-tag {
  padding: 2px 8px;
  background: rgba(76, 175, 80, 0.2);
  border-radius: 4px;
  font-size: 11px;
  color: var(--primary);
}

.defeat-section {
  text-align: center;
}

.defeat-text {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.defeat-hint {
  font-size: 12px;
  color: #f44336;
}

.result-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: center;
}

.confirm-btn {
  padding: 10px 40px;
  background: var(--primary);
  border: none;
  border-radius: 6px;
  color: #000;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.confirm-btn:hover {
  background: #5dbb63;
  transform: scale(1.05);
}
</style>

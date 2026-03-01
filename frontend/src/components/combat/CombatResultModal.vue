<template>
  <Teleport to="body">
    <Transition name="combat-result">
      <div
        v-if="combatStore.showResult"
        class="combat-result-overlay"
        :class="{ 'fade-out': fadeOut }"
        @click="handleClose"
      >
        <div class="combat-result-modal" :class="{ victory: isVictory, defeat: !isVictory }" @click.stop>
          <div class="result-icon">{{ isVictory ? '🏆' : '💀' }}</div>
          <div class="result-title">{{ isVictory ? '战斗胜利' : '战斗失败' }}</div>
          <div class="reward-divider"></div>

          <div class="result-content" v-if="combatStore.combatResult">
            <div class="reward-section" v-if="isVictory">
              <div class="reward-label">获得奖励</div>
              <div class="reward-list">
                <div class="reward-item" v-if="combatStore.combatResult.experience > 0">
                  <span class="item-name">经验</span>
                  <span class="item-quantity exp">+{{ combatStore.combatResult.experience }}</span>
                </div>
                <div class="reward-item" v-if="combatStore.combatResult.gold > 0">
                  <span class="item-name">金币</span>
                  <span class="item-quantity gold">+{{ combatStore.combatResult.gold }}</span>
                </div>
                <div
                  v-for="(item, index) in combatStore.combatResult.items"
                  :key="index"
                  class="reward-item"
                >
                  <span class="item-name">{{ item }}</span>
                </div>
                <div v-if="!hasAnyReward" class="empty-reward">
                  （没有获得任何战利品）
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
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useCombatStore } from '../../stores/combatStore'
import { useMapStore } from '../../stores/mapStore'

const combatStore = useCombatStore()
const mapStore = useMapStore()

const fadeOut = ref(false)

const isVictory = computed(() => {
  return combatStore.combatResult?.victory ?? false
})

const hasAnyReward = computed(() => {
  const result = combatStore.combatResult
  return result && (
    result.experience > 0 ||
    result.gold > 0 ||
    (result.items && result.items.length > 0)
  )
})

// 自动淡出定时器
let fadeOutTimer = null
let closeTimer = null

onMounted(() => {
  // 2秒后开始淡出
  fadeOutTimer = setTimeout(() => {
    fadeOut.value = true
  }, 2000)

  // 3秒后完全关闭
  closeTimer = setTimeout(() => {
    handleConfirm()
  }, 3000)
})

onUnmounted(() => {
  // 清理定时器
  if (fadeOutTimer) {
    clearTimeout(fadeOutTimer)
    fadeOutTimer = null
  }
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
})

function handleClose() {
  // 点击遮罩层关闭
  handleConfirm()
}

function handleConfirm() {
  // 清理定时器
  if (fadeOutTimer) clearTimeout(fadeOutTimer)
  if (closeTimer) clearTimeout(closeTimer)

  combatStore.closeCombatResult()
  combatStore.reset()
  // 战斗结束后切换到地图窗口
  mapStore.setWindowType('map')
}
</script>

<style scoped>
.combat-result-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  z-index: 2000;
  transition: opacity 1s ease-out;
}

.combat-result-overlay.fade-out {
  opacity: 0;
  pointer-events: none;
}

.combat-result-modal {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border: 2px solid #ffd700;
  border-radius: 12px;
  padding: 24px 32px;
  min-width: 280px;
  max-width: 400px;
  text-align: center;
  box-shadow: 0 0 30px rgba(255, 215, 0, 0.3), 0 8px 32px rgba(0, 0, 0, 0.5);
  animation: result-open 0.5s ease-out;
}

.combat-result-modal.defeat {
  border-color: #f44336;
  box-shadow: 0 0 30px rgba(244, 67, 54, 0.3), 0 8px 32px rgba(0, 0, 0, 0.5);
}

@keyframes result-open {
  0% {
    transform: scale(0.5);
    opacity: 0;
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}

.result-icon {
  font-size: 48px;
  margin-bottom: 8px;
  animation: bounce 0.6s ease-out;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.result-title {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 16px;
  text-shadow: 0 0 10px rgba(255, 215, 0, 0.5);
}

.victory .result-title {
  color: #ffd700;
}

.defeat .result-title {
  color: #f44336;
}

.reward-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #ffd700, transparent);
  margin: 12px 0;
}

.defeat .reward-divider {
  background: linear-gradient(90deg, transparent, #f44336, transparent);
}

.result-content {
  margin-top: 12px;
}

.reward-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reward-label {
  font-size: 12px;
  color: #888;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 2px;
}

.reward-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reward-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: rgba(255, 215, 0, 0.1);
  border: 1px solid rgba(255, 215, 0, 0.3);
  border-radius: 6px;
  animation: item-appear 0.3s ease-out backwards;
}

.reward-item:nth-child(1) { animation-delay: 0.1s; }
.reward-item:nth-child(2) { animation-delay: 0.2s; }
.reward-item:nth-child(3) { animation-delay: 0.3s; }
.reward-item:nth-child(4) { animation-delay: 0.4s; }
.reward-item:nth-child(5) { animation-delay: 0.5s; }
.reward-item:nth-child(6) { animation-delay: 0.6s; }
.reward-item:nth-child(7) { animation-delay: 0.7s; }
.reward-item:nth-child(8) { animation-delay: 0.8s; }
.reward-item:nth-child(n+9) { animation-delay: 0.9s; }

@keyframes item-appear {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.item-name {
  color: #fff;
  font-size: 14px;
}

.item-quantity {
  font-size: 14px;
  font-weight: bold;
}

.item-quantity.exp {
  color: #9c27b0;
}

.item-quantity.gold {
  color: #ffc107;
}

.empty-reward {
  color: #666;
  font-style: italic;
  padding: 12px;
}

.defeat-section {
  text-align: center;
  padding: 12px 0;
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

/* 进入动画 */
.combat-result-enter-active {
  animation: result-open 0.5s ease-out;
}

/* 离开时不需要动画，因为已经通过 fade-out 类淡出了 */
.combat-result-leave-active {
  transition: none;
}
</style>

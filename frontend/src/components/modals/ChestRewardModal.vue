<template>
  <Teleport to="body">
    <Transition name="chest-reward">
      <div
        v-if="uiStore.chestReward.visible"
        class="chest-reward-overlay"
        :class="{ 'fade-out': uiStore.chestReward.fadeOut }"
        @click="uiStore.closeChestReward"
      >
        <div class="chest-reward-modal" @click.stop>
          <div class="chest-icon">🎁</div>
          <div class="chest-title">{{ uiStore.chestReward.chestName }}</div>
          <div class="reward-divider"></div>
          <div class="reward-label">获得物品</div>
          <div class="reward-list">
            <div
              v-for="(item, index) in uiStore.chestReward.items"
              :key="index"
              class="reward-item"
            >
              <span class="item-name">{{ item.name }}</span>
              <span v-if="item.quantity > 1" class="item-quantity">x{{ item.quantity }}</span>
            </div>
            <div v-if="uiStore.chestReward.items.length === 0" class="empty-reward">
              （空空如也）
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { useUIStore } from '../../stores/uiStore'

const uiStore = useUIStore()
</script>

<style scoped>
.chest-reward-overlay {
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

.chest-reward-overlay.fade-out {
  opacity: 0;
  pointer-events: none;
}

.chest-reward-modal {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border: 2px solid #ffd700;
  border-radius: 12px;
  padding: 24px 32px;
  min-width: 280px;
  max-width: 400px;
  text-align: center;
  box-shadow: 0 0 30px rgba(255, 215, 0, 0.3), 0 8px 32px rgba(0, 0, 0, 0.5);
  animation: chest-open 0.5s ease-out;
}

@keyframes chest-open {
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

.chest-icon {
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

.chest-title {
  font-size: 20px;
  font-weight: bold;
  color: #ffd700;
  margin-bottom: 16px;
  text-shadow: 0 0 10px rgba(255, 215, 0, 0.5);
}

.reward-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #ffd700, transparent);
  margin: 12px 0;
}

.reward-label {
  font-size: 12px;
  color: #888;
  margin-bottom: 12px;
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
  justify-content: center;
  gap: 8px;
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
  color: #4CAF50;
  font-size: 14px;
  font-weight: bold;
}

.empty-reward {
  color: #666;
  font-style: italic;
  padding: 12px;
}

/* 进入动画 */
.chest-reward-enter-active {
  animation: chest-open 0.5s ease-out;
}

/* 离开时不需要动画，因为已经通过 fade-out 类淡出了 */
.chest-reward-leave-active {
  transition: none;
}
</style>

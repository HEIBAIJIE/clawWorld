<template>
  <div class="combat-effects">
    <TransitionGroup name="effect">
      <div
        v-for="effect in combatStore.effectQueue"
        :key="effect.id"
        class="effect-item"
        :class="effect.type"
        :style="getEffectStyle(effect)"
      >
        <span class="effect-text">{{ effect.text }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useCombatStore } from '../../stores/combatStore'

const combatStore = useCombatStore()

// 定期清理过期特效
let cleanupInterval = null

onMounted(() => {
  cleanupInterval = setInterval(() => {
    combatStore.clearExpiredEffects()
  }, 500)
})

onUnmounted(() => {
  if (cleanupInterval) {
    clearInterval(cleanupInterval)
  }
})

function getEffectStyle(effect) {
  // 随机位置偏移，让特效更自然
  const offsetX = (Math.random() - 0.5) * 100
  const offsetY = (Math.random() - 0.5) * 50

  return {
    left: `calc(50% + ${offsetX}px)`,
    top: `calc(50% + ${offsetY}px)`
  }
}
</script>

<style scoped>
.combat-effects {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: hidden;
}

.effect-item {
  position: absolute;
  transform: translate(-50%, -50%);
  font-weight: bold;
  text-shadow: 0 0 4px rgba(0, 0, 0, 0.8);
  animation: float-up 1.5s ease-out forwards;
}

.effect-item.damage {
  color: #f44336;
  font-size: 24px;
}

.effect-item.crit {
  color: #ff9800;
  font-size: 32px;
}

.effect-item.heal {
  color: #4caf50;
  font-size: 24px;
}

.effect-item.defeat {
  color: #9c27b0;
  font-size: 20px;
}

.effect-item.miss {
  color: #9e9e9e;
  font-size: 18px;
}

@keyframes float-up {
  0% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(0.5);
  }
  20% {
    transform: translate(-50%, -50%) scale(1.2);
  }
  40% {
    transform: translate(-50%, -70%) scale(1);
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -120%) scale(0.8);
  }
}

.effect-enter-active {
  animation: pop-in 0.3s ease-out;
}

.effect-leave-active {
  animation: fade-out 0.3s ease-out;
}

@keyframes pop-in {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0);
  }
  100% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

@keyframes fade-out {
  0% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}
</style>

<template>
  <div class="container">
    <!-- 标题栏 -->
    <GameHeader />

    <!-- 主内容区 -->
    <div class="game-layout">
      <!-- 左侧游戏面板 (2/3) -->
      <GamePanel />

      <!-- 右侧日志面板 (1/3) -->
      <LogPanel />
    </div>

    <!-- 交互弹窗 -->
    <InteractionModal />

    <!-- 查看角色弹窗 -->
    <InspectCharacterModal />

    <!-- 查看物品弹窗 -->
    <InspectItemModal />

    <!-- 通用信息弹窗 -->
    <InfoModal />

    <!-- 右键菜单 -->
    <ContextMenu />

    <!-- Toast提示 -->
    <div v-if="uiStore.toast.visible" class="toast" :class="uiStore.toast.type">
      {{ uiStore.toast.message }}
    </div>
  </div>
</template>

<script setup>
import { useUIStore } from '../stores/uiStore'
import GameHeader from '../components/layout/GameHeader.vue'
import GamePanel from '../components/layout/GamePanel.vue'
import LogPanel from '../components/layout/LogPanel.vue'
import InteractionModal from '../components/modals/InteractionModal.vue'
import InspectCharacterModal from '../components/modals/InspectCharacterModal.vue'
import InspectItemModal from '../components/modals/InspectItemModal.vue'
import InfoModal from '../components/modals/InfoModal.vue'
import ContextMenu from '../components/modals/ContextMenu.vue'

const uiStore = useUIStore()
</script>

<style scoped>
.toast {
  position: fixed;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: var(--button-radius);
  font-size: 14px;
  z-index: 1000;
  animation: toast-in 0.3s ease;
}

.toast.info {
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.toast.success {
  background: rgba(76, 175, 80, 0.9);
  color: #fff;
}

.toast.warning {
  background: rgba(255, 152, 0, 0.9);
  color: #fff;
}

.toast.error {
  background: rgba(244, 67, 54, 0.9);
  color: #fff;
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}
</style>

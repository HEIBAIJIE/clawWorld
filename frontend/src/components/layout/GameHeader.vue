<template>
  <div class="header">
    <h1>ClawWorld</h1>
    <div class="header-actions">
      <button class="sci-button config-agent-button" @click="showConfigModal = true">
        配置代理
      </button>
      <button class="sci-button logout-button" @click="handleLogout">
        登出
      </button>
    </div>

    <!-- 智能代理配置弹窗 -->
    <AgentConfigModal v-if="showConfigModal" @close="showConfigModal = false" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useSessionStore } from '../../stores/sessionStore'
import { usePlayerStore } from '../../stores/playerStore'
import { useMapStore } from '../../stores/mapStore'
import { useLogStore } from '../../stores/logStore'
import { useAgentStore } from '../../stores/agentStore'
import { gameApi } from '../../api/game'
import AgentConfigModal from '../agent/AgentConfigModal.vue'

const sessionStore = useSessionStore()
const playerStore = usePlayerStore()
const mapStore = useMapStore()
const logStore = useLogStore()
const agentStore = useAgentStore()

const showConfigModal = ref(false)

onMounted(() => {
  // 恢复智能代理配置
  agentStore.restoreConfig()
})

const handleLogout = async () => {
  try {
    // 关闭智能代理
    agentStore.disable()
    await gameApi.logout(sessionStore.sessionId)
  } catch (error) {
    console.error('登出失败:', error)
  } finally {
    sessionStore.clearSession()
    playerStore.reset()
    mapStore.reset()
    logStore.clear()
  }
}
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.header h1 {
  color: var(--primary);
  font-size: 20px;
  margin: 0;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.config-agent-button,
.logout-button {
  padding: 6px 12px;
  font-size: 12px;
}
</style>

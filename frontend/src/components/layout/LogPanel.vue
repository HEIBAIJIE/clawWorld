<template>
  <div class="log-panel">
    <!-- 日志显示区 -->
    <div class="log-display-wrapper">
      <div class="log-display sci-panel sci-scrollbar" ref="logDisplayRef">
        <div class="game-text" v-html="formattedGameText"></div>
      </div>
      <!-- Token计数器 -->
      <div class="token-counter">
        <span class="token-label">约</span>
        <span class="token-count">{{ logStore.formattedTokenCount }}</span>
        <span class="token-label">tokens</span>
      </div>
      <!-- 智能代理思考状态 -->
      <div class="agent-thinking" v-if="agentStore.isThinking">
        <span class="thinking-dot"></span>
        <span>AI思考中...</span>
      </div>
    </div>

    <!-- 指令输入区 -->
    <div class="command-input-section">
      <input
        v-model="commandInput"
        class="sci-input command-input-field"
        type="text"
        placeholder="输入指令..."
        @keyup.enter="handleSendCommand"
        :disabled="sessionStore.isWaiting || agentStore.isEnabled"
      />
      <button
        class="sci-button primary send-button"
        @click="handleSendCommand"
        :disabled="sessionStore.isWaiting || !commandInput.trim() || agentStore.isEnabled"
      >
        {{ sessionStore.isWaiting ? '等待...' : '发送' }}
      </button>
      <button
        class="sci-button agent-toggle"
        :class="{ active: agentStore.isEnabled }"
        @click="handleToggleAgent"
        :title="agentStore.isEnabled ? '关闭智能代理' : '开启智能代理'"
      >
        {{ agentStore.isEnabled ? '代理中' : '智能代理' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useSessionStore } from '../../stores/sessionStore'
import { useLogStore } from '../../stores/logStore'
import { useAgentStore } from '../../stores/agentStore'
import { useCommand } from '../../composables/useCommand'
import { useAgent } from '../../composables/useAgent'

const sessionStore = useSessionStore()
const logStore = useLogStore()
const agentStore = useAgentStore()
const { sendCommand } = useCommand()
const { startAgentLoop } = useAgent()

const commandInput = ref('')
const logDisplayRef = ref(null)

// 发送指令
const handleSendCommand = async () => {
  if (!commandInput.value.trim() || sessionStore.isWaiting || agentStore.isEnabled) {
    return
  }

  const command = commandInput.value.trim()
  commandInput.value = ''
  await sendCommand(command)
}

// 切换智能代理
const handleToggleAgent = async () => {
  if (agentStore.isEnabled) {
    agentStore.disable()
  } else {
    if (!agentStore.isConfigured) {
      alert('请先配置智能代理（点击右上角的"配置代理"按钮）')
      return
    }
    const success = agentStore.enable()
    if (success) {
      // 启动代理循环，传入当前文本窗口内容
      await startAgentLoop(logStore.rawText)
    }
  }
}

// 格式化游戏文本
const formattedGameText = computed(() => {
  if (!logStore.rawText) return ''

  const lines = logStore.rawText.split('\n')
  const formattedLines = lines.map(line => {
    // 解析日志格式: [来源][时间][类型][子类型]内容
    const logPattern = /^\[([^\]]+)\]\[([^\]]+)\]\[([^\]]+)\]\[([^\]]+)\](.*)$/
    const match = line.match(logPattern)

    if (match) {
      const [, source, time, type, subType, content] = match

      let sourceClass = 'log-source'
      if (source === '服务端') sourceClass += ' log-source-server'
      else if (source === '你') sourceClass += ' log-source-client'

      let typeClass = 'log-type'
      if (type === '背景') typeClass += ' log-type-background'
      else if (type === '窗口') typeClass += ' log-type-window'
      else if (type === '状态') typeClass += ' log-type-state'
      else if (type === '发送指令') typeClass += ' log-type-command'

      return `<div class="log-line">` +
        `<span class="${sourceClass}">[${source}]</span>` +
        `<span class="log-time">[${time}]</span>` +
        `<span class="${typeClass}">[${type}]</span>` +
        `<span class="log-subtype">[${subType}]</span>` +
        `<span class="log-content">${escapeHtml(content)}</span>` +
        `</div>`
    }

    if (line.startsWith('> ')) {
      return `<div class="user-input">${escapeHtml(line)}</div>`
    }

    return `<div class="plain-text">${escapeHtml(line)}</div>`
  })

  return formattedLines.join('')
})

// HTML转义
function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

// 自动滚动到底部
watch(() => logStore.rawText, async () => {
  await nextTick()
  if (logDisplayRef.value) {
    logDisplayRef.value.scrollTop = logDisplayRef.value.scrollHeight
  }
})
</script>

<style scoped>
.log-panel {
  display: flex;
  flex-direction: column;
}

.log-display-wrapper {
  flex: 1;
  position: relative;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.log-display {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
}

.token-counter {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(37, 37, 37, 0.9);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 10px;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
  z-index: 10;
}

.token-label {
  color: var(--text-muted);
}

.token-count {
  color: var(--primary);
  font-weight: 600;
}

.command-input-section {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.command-input-field {
  flex: 1;
  min-width: 0;
}

.send-button {
  flex-shrink: 0;
  width: 80px;
}

.agent-toggle {
  flex-shrink: 0;
  min-width: 80px;
  background: var(--bg-secondary);
  border-color: var(--border-color);
}

.agent-toggle.active {
  background: var(--primary);
  border-color: var(--primary);
  color: var(--bg-primary);
}

.agent-thinking {
  position: absolute;
  bottom: 8px;
  left: 8px;
  background: rgba(37, 37, 37, 0.9);
  border: 1px solid var(--primary);
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  z-index: 10;
}

.thinking-dot {
  width: 8px;
  height: 8px;
  background: var(--primary);
  border-radius: 50%;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}
</style>

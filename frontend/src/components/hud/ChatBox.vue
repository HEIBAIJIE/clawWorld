<template>
  <div class="chat-box">
    <!-- 聊天标签栏 -->
    <div class="chat-tabs">
      <button
        v-for="(name, key) in chatStore.channelNames"
        :key="key"
        class="chat-tab"
        :class="{ active: chatStore.activeChannel === key }"
        @click="chatStore.setActiveChannel(key)"
      >
        {{ name }}
        <span class="unread-dot" v-if="hasUnread(key)"></span>
      </button>
    </div>

    <!-- 聊天消息区 -->
    <div class="chat-messages sci-scrollbar" ref="messagesRef">
      <div
        v-for="msg in displayMessages"
        :key="msg.id"
        class="chat-message"
        :class="msg.channel"
      >
        <span class="msg-time">[{{ msg.time }}]</span>
        <span class="msg-channel">[{{ chatStore.channelNames[msg.channel] }}]</span>
        <span class="msg-sender">{{ msg.sender }}:</span>
        <span class="msg-content">{{ msg.content }}</span>
      </div>
      <div v-if="displayMessages.length === 0" class="chat-empty">
        暂无聊天消息
      </div>
    </div>

    <!-- 聊天输入区 -->
    <div class="chat-input-area">
      <select v-model="sendChannel" class="channel-select">
        <option value="world">世界</option>
        <option value="map">地图</option>
        <option value="party">队伍</option>
      </select>
      <input
        v-model="inputText"
        class="chat-input"
        type="text"
        placeholder="输入消息..."
        maxlength="30"
        @keyup.enter="sendMessage"
        :disabled="sessionStore.isWaiting"
      />
      <button
        class="send-btn"
        @click="sendMessage"
        :disabled="!inputText.trim() || sessionStore.isWaiting"
        title="回车发送"
      >
        ↵
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useChatStore } from '../../stores/chatStore'
import { useLogStore } from '../../stores/logStore'
import { useSessionStore } from '../../stores/sessionStore'
import { useCommand } from '../../composables/useCommand'

const chatStore = useChatStore()
const logStore = useLogStore()
const sessionStore = useSessionStore()
const { sendCommand } = useCommand()

const inputText = ref('')
const sendChannel = ref('world')
const messagesRef = ref(null)
const lastReadTime = ref({})

// 显示的消息（当前频道或全部）
const displayMessages = computed(() => {
  return chatStore.filteredMessages.slice(-50)
})

// 检查是否有未读消息
function hasUnread(channel) {
  if (chatStore.activeChannel === channel) return false
  const lastRead = lastReadTime.value[channel] || 0
  return chatStore.messages.some(msg =>
    msg.channel === channel && msg.timestamp > lastRead
  )
}

// 发送消息
async function sendMessage() {
  if (!inputText.value.trim() || sessionStore.isWaiting) return

  const channelMap = {
    world: 'world',
    map: 'map',
    party: 'party'
  }

  const command = `say ${channelMap[sendChannel.value]} ${inputText.value.trim()}`
  inputText.value = ''
  await sendCommand(command)
}

// 监听日志变化，解析聊天消息
watch(() => logStore.rawText, (newText) => {
  chatStore.parseFromLog(newText)
}, { immediate: true })

// 监听消息变化，自动滚动到底部
watch(() => chatStore.messages.length, async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
})

// 切换频道时更新已读时间
watch(() => chatStore.activeChannel, (channel) => {
  lastReadTime.value[channel] = Date.now()
})

onMounted(() => {
  // 初始化已读时间
  Object.keys(chatStore.channelNames).forEach(key => {
    lastReadTime.value[key] = Date.now()
  })
})
</script>

<style scoped>
.chat-box {
  position: absolute;
  bottom: 12px;
  left: 12px;
  z-index: 50;
  width: 320px;
  height: 220px;
  background: linear-gradient(135deg, rgba(20, 20, 25, 0.92) 0%, rgba(30, 30, 40, 0.88) 100%);
  border: 1px solid rgba(76, 175, 80, 0.25);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(8px);
  overflow: hidden;
}

.chat-tabs {
  display: flex;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.chat-tab {
  flex: 1;
  padding: 6px 8px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.chat-tab:hover {
  color: rgba(255, 255, 255, 0.8);
  background: rgba(255, 255, 255, 0.05);
}

.chat-tab.active {
  color: #4CAF50;
  background: rgba(76, 175, 80, 0.1);
  border-bottom: 2px solid #4CAF50;
}

.unread-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 6px;
  height: 6px;
  background: #ef5350;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.8); }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  font-size: 11px;
  line-height: 1.5;
}

.chat-message {
  margin-bottom: 4px;
  word-break: break-all;
}

.msg-time {
  color: rgba(255, 255, 255, 0.35);
  font-size: 10px;
}

.msg-channel {
  font-size: 10px;
  padding: 0 2px;
  border-radius: 2px;
  margin: 0 2px;
}

.chat-message.world .msg-channel {
  color: #ffb74d;
  background: rgba(255, 183, 77, 0.15);
}

.chat-message.map .msg-channel {
  color: #4fc3f7;
  background: rgba(79, 195, 247, 0.15);
}

.chat-message.party .msg-channel {
  color: #81c784;
  background: rgba(129, 199, 132, 0.15);
}

.chat-message.private .msg-channel {
  color: #f48fb1;
  background: rgba(244, 143, 177, 0.15);
}

.msg-sender {
  color: rgba(255, 255, 255, 0.8);
  font-weight: 500;
  margin-right: 4px;
}

.msg-content {
  color: rgba(255, 255, 255, 0.9);
}

.chat-empty {
  color: rgba(255, 255, 255, 0.3);
  text-align: center;
  padding: 20px;
  font-size: 12px;
}

.chat-input-area {
  display: flex;
  gap: 4px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.2);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.channel-select {
  width: 60px;
  padding: 4px 6px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
}

.channel-select:focus {
  outline: none;
  border-color: #4CAF50;
}

.channel-select option {
  background: #2a2a2a;
  color: #e0e0e0;
}

.chat-input {
  flex: 1;
  padding: 4px 8px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 4px;
  min-width: 0;
}

.chat-input:focus {
  outline: none;
  border-color: #4CAF50;
}

.chat-input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

.chat-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn {
  width: 32px;
  padding: 4px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(76, 175, 80, 0.3);
  border: 1px solid rgba(76, 175, 80, 0.5);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: rgba(76, 175, 80, 0.5);
  color: #fff;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 自定义滚动条 */
.chat-messages::-webkit-scrollbar {
  width: 4px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>

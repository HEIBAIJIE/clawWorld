<template>
  <div class="chat-box">
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
        <!-- 系统消息的操作按钮 -->
        <span v-if="msg.actionType" class="msg-actions">
          <button
            class="action-btn accept"
            @click="handleAccept(msg)"
            :disabled="sessionStore.isWaiting"
          >接受</button>
          <button
            class="action-btn reject"
            @click="handleReject(msg)"
            :disabled="sessionStore.isWaiting"
          >拒绝</button>
        </span>
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
import { ref, computed, watch, nextTick } from 'vue'
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

// 显示所有消息（最近50条）
const displayMessages = computed(() => {
  return chatStore.messages.slice(-50)
})

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

// 处理接受操作
async function handleAccept(msg) {
  if (sessionStore.isWaiting) return

  let command = ''
  if (msg.actionType === 'party_invite') {
    command = `interact ${msg.actionTarget} 接受组队邀请`
  } else if (msg.actionType === 'trade_invite') {
    command = `interact ${msg.actionTarget} 接受交易请求`
  }

  if (command) {
    // 移除该系统消息
    chatStore.removeSystemMessage(msg.actionType, msg.actionTarget)
    await sendCommand(command)
  }
}

// 处理拒绝操作
async function handleReject(msg) {
  if (sessionStore.isWaiting) return

  let command = ''
  if (msg.actionType === 'party_invite') {
    command = `interact ${msg.actionTarget} 拒绝组队邀请`
  } else if (msg.actionType === 'trade_invite') {
    command = `interact ${msg.actionTarget} 拒绝交易请求`
  }

  if (command) {
    // 移除该系统消息
    chatStore.removeSystemMessage(msg.actionType, msg.actionTarget)
    await sendCommand(command)
  }
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
</script>

<style scoped>
.chat-box {
  position: absolute;
  bottom: 40px;
  left: 12px;
  z-index: 50;
  width: 360px;
  height: 200px;
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
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
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

.chat-message.system .msg-channel {
  color: #ffd54f;
  background: rgba(255, 213, 79, 0.2);
}

.msg-sender {
  color: rgba(255, 255, 255, 0.8);
  font-weight: 500;
  margin-right: 4px;
}

.msg-content {
  color: rgba(255, 255, 255, 0.9);
}

.msg-actions {
  display: inline-flex;
  gap: 4px;
  margin-left: 6px;
}

.action-btn {
  padding: 2px 8px;
  font-size: 10px;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.action-btn.accept {
  background: rgba(76, 175, 80, 0.4);
  color: #81c784;
  border: 1px solid rgba(76, 175, 80, 0.5);
}

.action-btn.accept:hover:not(:disabled) {
  background: rgba(76, 175, 80, 0.6);
  color: #fff;
}

.action-btn.reject {
  background: rgba(239, 83, 80, 0.3);
  color: #ef9a9a;
  border: 1px solid rgba(239, 83, 80, 0.4);
}

.action-btn.reject:hover:not(:disabled) {
  background: rgba(239, 83, 80, 0.5);
  color: #fff;
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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

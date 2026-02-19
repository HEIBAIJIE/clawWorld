import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 聊天消息列表（已去重）
  const messages = ref([])

  // 当前选中的聊天频道
  const activeChannel = ref('world') // world, map, party

  // 频道名称映射
  const channelNames = {
    world: '世界',
    map: '地图',
    party: '队伍',
    private: '私聊'
  }

  // 按频道过滤的消息
  const filteredMessages = computed(() => {
    if (activeChannel.value === 'all') {
      return messages.value
    }
    return messages.value.filter(msg => msg.channel === activeChannel.value)
  })

  // 添加聊天消息（自动去重）
  function addMessage(channel, sender, content, timestamp) {
    // 生成唯一ID用于去重
    const msgId = `${channel}-${sender}-${content}-${timestamp}`

    // 检查是否已存在
    const exists = messages.value.some(msg => msg.id === msgId)
    if (exists) {
      return false
    }

    messages.value.push({
      id: msgId,
      channel,
      sender,
      content,
      timestamp,
      time: new Date(timestamp).toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit'
      })
    })

    // 保留最近200条消息
    if (messages.value.length > 200) {
      messages.value = messages.value.slice(-200)
    }

    return true
  }

  // 从日志文本解析聊天消息
  function parseFromLog(logText) {
    if (!logText) return

    const lines = logText.split('\n')

    for (const line of lines) {
      // 解析格式1: [服务端][时间][状态][新增聊天][频道] 发送者: 内容
      const stateMatch = line.match(/\[服务端\]\[([^\]]+)\]\[状态\]\[新增聊天\]\[([^\]]+)\]\s*([^:：]+)[：:]\s*(.+)/)
      if (stateMatch) {
        const [, timeStr, channelCn, sender, content] = stateMatch
        const channel = getChannelKey(channelCn)
        const timestamp = parseTimeStr(timeStr)
        addMessage(channel, sender.trim(), content.trim(), timestamp)
        continue
      }

      // 解析格式2: [频道] 发送者: 内容 (在窗口聊天记录中)
      const chatMatch = line.match(/^\[([世界地图队伍私聊]+)\]\s*([^:：]+)[：:]\s*(.+)/)
      if (chatMatch) {
        const [, channelCn, sender, content] = chatMatch
        const channel = getChannelKey(channelCn)
        // 使用当前时间作为时间戳
        addMessage(channel, sender.trim(), content.trim(), Date.now())
      }
    }
  }

  // 中文频道名转key
  function getChannelKey(channelCn) {
    const map = {
      '世界': 'world',
      '地图': 'map',
      '队伍': 'party',
      '私聊': 'private'
    }
    return map[channelCn] || 'world'
  }

  // 解析时间字符串
  function parseTimeStr(timeStr) {
    // 格式: 2月20日 03:25:20
    const match = timeStr.match(/(\d+)月(\d+)日\s+(\d+):(\d+):(\d+)/)
    if (match) {
      const [, month, day, hour, minute, second] = match
      const now = new Date()
      const date = new Date(now.getFullYear(), parseInt(month) - 1, parseInt(day),
        parseInt(hour), parseInt(minute), parseInt(second))
      return date.getTime()
    }
    return Date.now()
  }

  // 设置当前频道
  function setActiveChannel(channel) {
    activeChannel.value = channel
  }

  // 清空消息
  function clear() {
    messages.value = []
  }

  return {
    messages,
    activeChannel,
    channelNames,
    filteredMessages,
    addMessage,
    parseFromLog,
    setActiveChannel,
    clear
  }
})

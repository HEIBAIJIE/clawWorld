import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 聊天消息列表（已去重）
  const messages = ref([])

  // 频道名称映射
  const channelNames = {
    world: '世界',
    map: '地图',
    party: '队伍',
    private: '私聊',
    system: '系统'
  }

  // 添加聊天消息（自动去重）
  function addMessage(channel, sender, content, timestamp, actionType = null, actionTarget = null) {
    // 生成唯一ID用于去重（不包含时间戳，因为窗口消息每次都是新时间戳）
    const msgId = `${channel}-${sender}-${content}`

    // 检查是否已存在相同内容的消息
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
      }),
      actionType,  // 'party_invite' | 'trade_invite' | null
      actionTarget // 邀请者名称
    })

    // 保留最近200条消息
    if (messages.value.length > 200) {
      messages.value = messages.value.slice(-200)
    }

    return true
  }

  // 添加系统消息（带操作按钮）
  function addSystemMessage(content, actionType = null, actionTarget = null) {
    return addMessage('system', '系统', content, Date.now(), actionType, actionTarget)
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
        continue
      }

      // 解析组队邀请: [服务端][时间][状态][队伍变化]XXX 邀请你加入队伍
      const partyInviteMatch = line.match(/\[服务端\]\[([^\]]+)\]\[状态\]\[队伍变化\](.+)\s+邀请你加入队伍/)
      if (partyInviteMatch) {
        const [, , inviterName] = partyInviteMatch
        addSystemMessage(`${inviterName.trim()} 邀请你加入队伍`, 'party_invite', inviterName.trim())
        continue
      }

      // 解析交易邀请: [服务端][时间][状态][交易变化]XXX 邀请你进行交易
      const tradeInviteMatch = line.match(/\[服务端\]\[([^\]]+)\]\[状态\]\[交易变化\](.+)\s+邀请你进行交易/)
      if (tradeInviteMatch) {
        const [, , inviterName] = tradeInviteMatch
        addSystemMessage(`${inviterName.trim()} 邀请你进行交易`, 'trade_invite', inviterName.trim())
        continue
      }
    }
  }

  // 中文频道名转key
  function getChannelKey(channelCn) {
    const map = {
      '世界': 'world',
      '地图': 'map',
      '队伍': 'party',
      '私聊': 'private',
      '系统': 'system'
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

  // 清除特定系统消息的操作按钮（当邀请被处理后）
  function removeSystemMessage(actionType, actionTarget) {
    const msg = messages.value.find(
      msg => msg.actionType === actionType && msg.actionTarget === actionTarget
    )
    if (msg) {
      // 只清除 actionType，保留消息本身，这样按钮会消失但消息还在
      msg.actionType = null
      msg.actionTarget = null
    }
  }

  // 清空消息
  function clear() {
    messages.value = []
  }

  return {
    messages,
    channelNames,
    addMessage,
    addSystemMessage,
    parseFromLog,
    removeSystemMessage,
    clear
  }
})

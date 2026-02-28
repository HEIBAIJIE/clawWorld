/**
 * 日志格式解析器
 * 解析服务端返回的日志格式：[来源][时间][类型][子类型]内容
 */

// 日志行正则 - 新格式：[时间][类型][子类型]内容
const LOG_PATTERN = /^\[([^\]]+)\]\[([^\]]+)\]\[([^\]]+)\](.*)$/

/**
 * 解析时间字符串为毫秒时间戳
 * 格式：HH:MM
 * @param {string} timeStr - 时间字符串
 * @returns {number} 毫秒时间戳，解析失败返回0
 */
export function parseTimeToTimestamp(timeStr) {
  if (!timeStr) return 0

  // 匹配格式：HH:MM
  const match = timeStr.match(/(\d+):(\d+)/)
  if (!match) return 0

  const hour = parseInt(match[1])
  const minute = parseInt(match[2])

  // 使用当前日期
  const now = new Date()
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, minute, 0)
  return date.getTime()
}

/**
 * 解析单行日志
 * @param {string} line - 日志行
 * @returns {object} 解析结果
 */
export function parseLogLine(line) {
  const match = line.match(LOG_PATTERN)
  if (match) {
    const timeStr = match[1]
    return {
      isLog: true,
      source: 'SERVER', // 新格式不再包含来源，默认为服务端
      time: timeStr,
      timestamp: parseTimeToTimestamp(timeStr),
      type: match[2],
      subType: match[3],
      content: match[4].trim()
    }
  }

  // 用户输入
  if (line.startsWith('> ')) {
    return {
      isLog: false,
      type: 'user-input',
      content: line.substring(2)
    }
  }

  // 普通文本（可能是多行日志的后续行）
  return {
    isLog: false,
    type: 'plain',
    content: line
  }
}

/**
 * 解析多行日志文本，将多行内容合并到对应的日志条目
 * @param {string} text - 多行日志文本
 * @returns {array} 解析后的日志数组
 */
export function parseLogText(text) {
  if (!text) return []

  const lines = text.split('\n')
  const entries = []
  let currentEntry = null

  for (const line of lines) {
    if (!line.trim()) continue

    const parsed = parseLogLine(line)

    if (parsed.isLog) {
      // 新的日志条目
      if (currentEntry) {
        entries.push(currentEntry)
      }
      currentEntry = parsed
    } else if (currentEntry) {
      // 将普通文本追加到当前日志条目的内容中
      currentEntry.content += '\n' + parsed.content
    } else {
      // 没有当前条目，作为独立条目
      entries.push(parsed)
    }
  }

  // 添加最后一个条目
  if (currentEntry) {
    entries.push(currentEntry)
  }

  console.log('[LogParser] 解析日志文本，共', entries.length, '个条目')
  return entries
}

/**
 * 按类型分组日志
 * @param {array} entries - 日志条目数组
 * @returns {object} 分组后的日志
 */
export function groupLogsByType(entries) {
  const grouped = {
    background: [],
    window: [],
    state: [],
    command: [],
    other: []
  }

  for (const entry of entries) {
    if (!entry.isLog) {
      grouped.other.push(entry)
      continue
    }

    switch (entry.type) {
      case '背景':
        grouped.background.push(entry)
        break
      case '窗口':
        grouped.window.push(entry)
        break
      case '状态':
        grouped.state.push(entry)
        break
      case '发送指令':
        grouped.command.push(entry)
        break
      default:
        grouped.other.push(entry)
    }
  }

  return grouped
}

/**
 * 提取特定子类型的日志内容
 * @param {array} entries - 日志条目数组
 * @param {string} type - 类型
 * @param {string} subType - 子类型
 * @returns {array} 匹配的内容数组
 */
export function extractBySubType(entries, type, subType) {
  return entries
    .filter(e => e.isLog && e.type === type && e.subType === subType)
    .map(e => e.content)
}

export default {
  parseLogLine,
  parseLogText,
  groupLogsByType,
  extractBySubType,
  parseTimeToTimestamp
}

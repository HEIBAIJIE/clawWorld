/**
 * 战斗数据解析器
 * 解析战斗窗口的各种信息
 */

/**
 * 解析参战方
 * 输入格式:
 * "【参战方】"
 * "第1方（PARTY_xxx）：小小 巧巧"
 * "第2方（enemy_slime）：史莱姆#1"
 * @param {string} content - 参战方文本
 * @returns {array} 参战方数组
 */
export function parseFactions(content) {
  const factions = []
  const factionPattern = /第(\d+)方（([^）]+)）[：:]\s*(.+)/g
  let match

  while ((match = factionPattern.exec(content)) !== null) {
    const members = match[3].trim().split(/\s+/).filter(Boolean)
    factions.push({
      index: parseInt(match[1]),
      id: match[2],
      members
    })
  }

  return factions
}

/**
 * 解析角色状态
 * 输入格式:
 * "♥ 小小 - HP:116/135 MP:50/55 速度:105 阵营:PARTY_xxx (你)"
 * "☠ 史莱姆#1 - HP:0/50 MP:0/0 速度:80 阵营:enemy_wolf"
 * @param {string} content - 角色状态文本
 * @returns {array} 角色状态数组
 */
export function parseCharacterStatus(content) {
  const characters = []
  const lines = content.split('\n')
  // 支持带阵营和不带阵营的格式
  // 格式: ♥ 小小 - HP:146/180 MP:55/65 速度:111 阵营:PLAYER_xxx (你)
  const statusPattern = /^([♥☠])\s*(.+?)\s*-\s*HP[：:](\d+)\/(\d+)\s*MP[：:](\d+)\/(\d+)(?:\s*速度[：:](\d+))?(?:\s*阵营[：:]([^\s(]+))?/

  for (const line of lines) {
    const match = line.trim().match(statusPattern)
    if (match) {
      characters.push({
        name: match[2].trim(),
        isDead: match[1] === '☠',
        currentHealth: parseInt(match[3]),
        maxHealth: parseInt(match[4]),
        currentMana: parseInt(match[5]),
        maxMana: parseInt(match[6]),
        speed: match[7] ? parseInt(match[7]) : null,
        faction: match[8] || null,
        isSelf: line.includes('(你)')
      })
    }
  }

  return characters
}

/**
 * 解析行动条
 * 输入格式:
 * "→ 1. 小小 (100.0%) ← 你"
 * "  2. 史莱姆#1 (52.4%)"
 * @param {string} content - 行动条文本
 * @returns {array} 行动条数组
 */
export function parseActionBar(content) {
  const actionBar = []
  const lines = content.split('\n')
  const barPattern = /^(→)?\s*(\d+)\.\s*(.+?)\s*\(([\d.]+)%\)(?:\s*←\s*你)?$/

  for (const line of lines) {
    const match = line.trim().match(barPattern)
    if (match) {
      actionBar.push({
        position: parseInt(match[2]),
        name: match[3].trim(),
        progress: parseFloat(match[4]),
        isCurrent: match[1] === '→',
        isSelf: line.includes('← 你')
      })
    }
  }

  return actionBar
}

/**
 * 解析战斗日志
 * 输入格式:
 * "[#1] 小小 对 史莱姆#1 使用了 普通攻击"
 * "[#2] 造成了 29 点伤害"
 * @param {string} content - 战斗日志文本
 * @returns {array} 战斗日志数组
 */
export function parseBattleLogs(content) {
  const logs = []
  const lines = content.split('\n')
  const logPattern = /^\[#(\d+)\]\s*(.+)$/

  for (const line of lines) {
    const match = line.trim().match(logPattern)
    if (match) {
      logs.push({
        index: parseInt(match[1]),
        content: match[2].trim()
      })
    }
  }

  return logs
}

/**
 * 解析当前状态
 * @param {string} content - 状态文本
 * @returns {object} 状态信息
 */
export function parseCombatStatus(content) {
  return {
    isMyTurn: content.includes('★ 轮到你的回合'),
    isWaiting: content.includes('未轮到你的回合'),
    waitingFor: extractWaitingFor(content),
    needsAutoWait: needsAutoWait(content)
  }
}

/**
 * 提取等待的角色名
 */
function extractWaitingFor(content) {
  const match = content.match(/等待\s*(.+?)\s*行动/)
  return match ? match[1] : null
}

/**
 * 检测是否需要自动wait
 * @param {string} content - 状态文本
 * @returns {boolean}
 */
export function needsAutoWait(content) {
  return content.includes('未轮到你的回合，请输入wait继续等待')
}

/**
 * 解析战斗结果
 * @param {string} content - 结果文本
 * @returns {object|null} 战斗结果
 */
export function parseCombatResult(content) {
  // 胜利
  if (content.includes('获得胜利')) {
    const expMatch = content.match(/每人获得经验[：:]\s*(\d+)/)
    const goldMatch = content.match(/每人\s*(\d+)/)
    const factionMatch = content.match(/阵营\s*(\S+)\s*获得胜利/)

    return {
      victory: true,
      winnerFaction: factionMatch ? factionMatch[1] : null,
      experience: expMatch ? parseInt(expMatch[1]) : 0,
      gold: goldMatch ? parseInt(goldMatch[1]) : 0
    }
  }

  // 失败
  if (content.includes('战斗失败') || content.includes('被击败')) {
    return {
      victory: false,
      experience: 0,
      gold: 0
    }
  }

  return null
}

/**
 * 解析技能列表
 * 输入格式:
 * "- 普通攻击 [敌方单体] (消耗:0MP, 无CD)"
 * "- 火球术 [敌方单体] (消耗:10MP, CD:2回合)"
 * @param {string} content - 技能列表文本
 * @returns {array} 技能数组
 */
export function parseSkillList(content) {
  const skills = []
  const lines = content.split('\n')
  const skillPattern = /^-\s*(.+?)\s*\[([^\]]+)\]\s*\(消耗[：:](\d+)MP(?:,\s*(?:无CD|CD[：:](\d+)回合))?\)/

  for (const line of lines) {
    const match = line.trim().match(skillPattern)
    if (match) {
      skills.push({
        name: match[1].trim(),
        targetType: parseTargetType(match[2]),
        targetTypeText: match[2],
        manaCost: parseInt(match[3]),
        cooldown: match[4] ? parseInt(match[4]) : 0
      })
    }
  }

  return skills
}

/**
 * 解析目标类型
 */
function parseTargetType(text) {
  switch (text) {
    case '敌方单体': return 'ENEMY_SINGLE'
    case '敌方群体': return 'ENEMY_ALL'
    case '我方单体': return 'ALLY_SINGLE'
    case '我方群体': return 'ALLY_ALL'
    case '自己': return 'SELF'
    default: return 'UNKNOWN'
  }
}

/**
 * 解析战斗动作（从战斗日志中提取）
 * @param {string} content - 日志内容（可能带有 [#序号] 前缀）
 * @returns {object|null} 动作信息
 */
export function parseCombatAction(content) {
  // 去掉可能的序号前缀 "[#序号] "
  const logMatch = content.match(/^\[#\d+\]\s*(.+)$/)
  const actionContent = logMatch ? logMatch[1] : content

  // 使用技能: "小小 对 史莱姆#1 使用了 普通攻击"
  const skillMatch = actionContent.match(/^(.+?)\s+对\s+(.+?)\s+使用了\s+(.+)$/)
  if (skillMatch) {
    return {
      type: 'skill',
      attacker: skillMatch[1].trim(),
      target: skillMatch[2].trim(),
      skillName: skillMatch[3].trim()
    }
  }

  // 造成伤害: "造成了 29 点伤害" 或 "造成了 29 点伤害（暴击！）"
  const damageMatch = actionContent.match(/^造成了\s*(\d+)\s*点伤害(?:（(暴击！?)）)?$/)
  if (damageMatch) {
    return {
      type: 'damage',
      amount: parseInt(damageMatch[1]),
      isCrit: !!damageMatch[2]
    }
  }

  // 治疗: "恢复了 50 点生命值"
  const healMatch = actionContent.match(/^恢复了\s*(\d+)\s*点生命值$/)
  if (healMatch) {
    return {
      type: 'heal',
      amount: parseInt(healMatch[1])
    }
  }

  // 击败: "史莱姆#1 被击败了！"
  const defeatMatch = actionContent.match(/^(.+?)\s*被击败了！?$/)
  if (defeatMatch) {
    return {
      type: 'defeat',
      target: defeatMatch[1].trim()
    }
  }

  // 回合开始: "=== 轮到 小小 的回合 ==="
  const turnMatch = actionContent.match(/^===\s*轮到\s*(.+?)\s*的回合\s*===$/)
  if (turnMatch) {
    return {
      type: 'turn',
      character: turnMatch[1].trim()
    }
  }

  // 等待行动: "小小 等待行动..."
  const waitMatch = actionContent.match(/^(.+?)\s*等待行动/)
  if (waitMatch) {
    return {
      type: 'wait',
      character: waitMatch[1].trim()
    }
  }

  // 胜利: "阵营 xxx 获得胜利！"
  const victoryMatch = actionContent.match(/^阵营\s*(\S+)\s*获得胜利/)
  if (victoryMatch) {
    return {
      type: 'victory',
      faction: victoryMatch[1]
    }
  }

  // 战利品分配
  if (actionContent.includes('战利品分配')) {
    return { type: 'loot_header' }
  }

  // 经验分配: "每人获得经验: 200 (共2人)" 或 "小小 获得经验: 100"
  const expMatch = actionContent.match(/获得经验[：:]\s*(\d+)/)
  if (expMatch) {
    return {
      type: 'exp',
      amount: parseInt(expMatch[1])
    }
  }

  // 金钱分配: "金钱平分: 每人 50 (总计100)" 或 "小小 获得金钱: 50"
  const goldMatch = actionContent.match(/(?:金钱平分[：:]\s*每人\s*|获得金钱[：:]\s*)(\d+)/)
  if (goldMatch) {
    return {
      type: 'gold',
      amount: parseInt(goldMatch[1])
    }
  }

  return null
}

/**
 * 从指令响应中解析战斗结果
 * @param {string} content - 指令响应内容
 * @returns {object|null} 战斗结果
 */
export function parseCommandResponse(content) {
  const result = {
    actions: [],
    combatEnd: null
  }

  const lines = content.split('\n')
  let currentAction = null

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) continue

    // 检查是否是带序号的日志
    const logMatch = trimmed.match(/^\[#\d+\]\s*(.+)$/)
    const actionContent = logMatch ? logMatch[1] : trimmed

    const action = parseCombatAction(actionContent)

    if (action) {
      if (action.type === 'skill') {
        currentAction = action
        result.actions.push(action)
      } else if (action.type === 'damage' && currentAction) {
        currentAction.damage = action.amount
        currentAction.isCrit = action.isCrit
      } else if (action.type === 'defeat') {
        result.actions.push(action)
      } else if (action.type === 'victory') {
        result.combatEnd = {
          victory: true,
          winnerFaction: action.faction
        }
      } else if (action.type === 'exp') {
        if (result.combatEnd) {
          result.combatEnd.experience = action.amount
        }
      } else if (action.type === 'gold') {
        if (result.combatEnd) {
          result.combatEnd.gold = action.amount
        }
      }
    }
  }

  return result
}

export default {
  parseFactions,
  parseCharacterStatus,
  parseActionBar,
  parseBattleLogs,
  parseCombatStatus,
  parseCombatResult,
  parseSkillList,
  parseCombatAction,
  parseCommandResponse,
  needsAutoWait
}

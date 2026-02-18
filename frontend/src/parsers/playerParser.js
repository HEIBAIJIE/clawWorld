/**
 * 玩家状态解析器
 * 解析玩家属性、技能、装备、背包等信息
 */

/**
 * 解析玩家状态块
 * @param {string} content - 玩家状态文本
 * @returns {object} 玩家状态对象
 */
export function parsePlayerState(content) {
  const result = {}

  // 解析角色基础信息: "角色: 小小 (战士) Lv.2"
  const basicMatch = content.match(/角色[：:]\s*(.+?)\s*\((.+?)\)\s*Lv\.(\d+)/)
  if (basicMatch) {
    result.name = basicMatch[1].trim()
    result.roleName = basicMatch[2].trim()
    result.level = parseInt(basicMatch[3])
  }

  // 解析位置: "位置: (8, 7)"
  const posMatch = content.match(/位置[：:]\s*\((\d+),\s*(\d+)\)/)
  if (posMatch) {
    result.x = parseInt(posMatch[1])
    result.y = parseInt(posMatch[2])
  }

  // 解析经验和金币: "经验: 100/300 (33%)  金币: 134"
  const expMatch = content.match(/经验[：:]\s*(\d+)\/(\d+)/)
  if (expMatch) {
    result.experience = parseInt(expMatch[1])
    result.experienceForNextLevel = parseInt(expMatch[2])
  }

  const goldMatch = content.match(/金币[：:]\s*(\d+)/)
  if (goldMatch) {
    result.gold = parseInt(goldMatch[1])
  }

  // 解析四维属性: "力量3 敏捷2 智力0 体力0"
  const attrMatch = content.match(/力量(\d+)\s*敏捷(\d+)\s*智力(\d+)\s*体力(\d+)/)
  if (attrMatch) {
    result.strength = parseInt(attrMatch[1])
    result.agility = parseInt(attrMatch[2])
    result.intelligence = parseInt(attrMatch[3])
    result.vitality = parseInt(attrMatch[4])
  }

  // 解析生命法力: "生命116/135 法力50/55"
  const hpMpMatch = content.match(/生命(\d+)\/(\d+)\s*法力(\d+)\/(\d+)/)
  if (hpMpMatch) {
    result.currentHealth = parseInt(hpMpMatch[1])
    result.maxHealth = parseInt(hpMpMatch[2])
    result.currentMana = parseInt(hpMpMatch[3])
    result.maxMana = parseInt(hpMpMatch[4])
  }

  // 解析战斗属性: "物攻34 物防20 法攻5 法防9 速度105"
  const combatMatch = content.match(/物攻(\d+)\s*物防(\d+)\s*法攻(\d+)\s*法防(\d+)\s*速度(\d+)/)
  if (combatMatch) {
    result.physicalAttack = parseInt(combatMatch[1])
    result.physicalDefense = parseInt(combatMatch[2])
    result.magicAttack = parseInt(combatMatch[3])
    result.magicDefense = parseInt(combatMatch[4])
    result.speed = parseInt(combatMatch[5])
  }

  // 解析暴击等属性: "暴击率6.5% 暴击伤害53.0% 命中率101.7% 闪避率5.7%"
  const critMatch = content.match(/暴击率([\d.]+)%/)
  if (critMatch) result.critRate = parseFloat(critMatch[1])

  const critDmgMatch = content.match(/暴击伤害([\d.]+)%/)
  if (critDmgMatch) result.critDamage = parseFloat(critDmgMatch[1])

  const hitMatch = content.match(/命中率([\d.]+)%/)
  if (hitMatch) result.hitRate = parseFloat(hitMatch[1])

  const dodgeMatch = content.match(/闪避率([\d.]+)%/)
  if (dodgeMatch) result.dodgeRate = parseFloat(dodgeMatch[1])

  // 解析可用属性点: "可用属性点: 5"
  const freePointsMatch = content.match(/可用属性点[：:]\s*(\d+)/)
  if (freePointsMatch) {
    result.freeAttributePoints = parseInt(freePointsMatch[1])
  }

  return result
}

/**
 * 解析技能列表
 * 输入格式: "- 普通攻击 [敌方单体] (消耗:0MP, 无CD) - 基础物理攻击"
 * @param {string} content - 技能列表文本
 * @returns {array} 技能数组
 */
export function parseSkills(content) {
  const lines = content.split('\n')
  const skills = []
  const skillPattern = /^-\s*(.+?)\s+\[([^\]]+)\]\s+\(消耗[：:](\d+)MP,\s*(?:CD[：:](\d+)回合|无CD)\)(?:\s*-\s*(.+))?/

  for (const line of lines) {
    const match = line.trim().match(skillPattern)
    if (match) {
      skills.push({
        name: match[1].trim(),
        targetType: match[2].trim(),
        manaCost: parseInt(match[3]),
        cooldown: match[4] ? parseInt(match[4]) : 0,
        description: match[5] ? match[5].trim() : ''
      })
    }
  }

  return skills
}

/**
 * 解析装备栏
 * 输入格式:
 * "头部: 铁盔甲"
 * "上装: 无"
 * @param {string} content - 装备栏文本
 * @returns {object} 装备对象
 */
export function parseEquipment(content) {
  const equipment = {
    HEAD: null,
    CHEST: null,
    LEGS: null,
    FEET: null,
    LEFT_HAND: null,
    RIGHT_HAND: null,
    ACCESSORY1: null,
    ACCESSORY2: null
  }

  const slotMap = {
    '头部': 'HEAD',
    '上装': 'CHEST',
    '下装': 'LEGS',
    '鞋子': 'FEET',
    '左手': 'LEFT_HAND',
    '右手': 'RIGHT_HAND',
    '饰品1': 'ACCESSORY1',
    '饰品2': 'ACCESSORY2'
  }

  const lines = content.split('\n')
  for (const line of lines) {
    const match = line.match(/^(.+?)[：:]\s*(.+)$/)
    if (match) {
      const slotName = match[1].trim()
      const itemName = match[2].trim()
      const slot = slotMap[slotName]
      if (slot) {
        equipment[slot] = itemName === '无' || itemName === '无装备' ? null : { name: itemName }
      }
    }
  }

  return equipment
}

/**
 * 解析背包
 * 输入格式:
 * "小型生命药水 x1"
 * "铁剑#1"
 * @param {string} content - 背包文本
 * @returns {array} 背包物品数组
 */
export function parseInventory(content) {
  const inventory = []
  const lines = content.split('\n')

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed || trimmed === '背包为空') continue
    // 跳过标题行
    if (trimmed.includes('你的背包') || trimmed.startsWith('背包')) continue

    // 带数量的物品: "小型生命药水 x1"
    const stackMatch = trimmed.match(/^(.+?)\s+x(\d+)$/)
    if (stackMatch) {
      inventory.push({
        name: stackMatch[1].trim(),
        quantity: parseInt(stackMatch[2]),
        isEquipment: false
      })
      continue
    }

    // 装备（带#编号）: "铁剑#1"
    if (trimmed.includes('#')) {
      inventory.push({
        name: trimmed,
        quantity: 1,
        isEquipment: true
      })
      continue
    }

    // 其他物品
    if (trimmed) {
      inventory.push({
        name: trimmed,
        quantity: 1,
        isEquipment: false
      })
    }
  }

  return inventory
}

/**
 * 解析队伍信息
 * 输入格式:
 * "你是队长"
 * "队伍成员(2/4)："
 * "  - 小小 [队长]"
 * "  - 巧巧"
 * @param {string} content - 队伍信息文本
 * @returns {object} 队伍信息
 */
export function parsePartyInfo(content) {
  const result = {
    isInParty: false,
    isLeader: false,
    members: [],
    maxMembers: 4
  }

  // 检查是否在队伍中
  if (content.includes('你是队长') || content.includes('你在队伍中')) {
    result.isInParty = true
    result.isLeader = content.includes('你是队长')
  }

  // 解析成员数量
  const countMatch = content.match(/队伍成员\((\d+)\/(\d+)\)/)
  if (countMatch) {
    result.maxMembers = parseInt(countMatch[2])
  }

  // 解析成员列表
  const memberPattern = /^\s*-\s*(.+?)(?:\s+\[队长\])?$/gm
  let match
  while ((match = memberPattern.exec(content)) !== null) {
    const name = match[1].trim()
    const isLeader = match[0].includes('[队长]')
    result.members.push({
      name,
      isLeader
    })
  }

  return result
}

export default {
  parsePlayerState,
  parseSkills,
  parseEquipment,
  parseInventory,
  parsePartyInfo
}

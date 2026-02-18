import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useCombatStore = defineStore('combat', () => {
  // 战斗状态
  const isInCombat = ref(false)
  const combatId = ref(null)

  // 参战方
  const factions = ref([])

  // 所有战斗角色
  const characters = ref([])

  // 行动条
  const actionBar = ref([])

  // 当前回合
  const currentTurn = ref(null)
  const isMyTurn = ref(false)
  const currentActorId = ref(null)

  // 战斗日志
  const battleLogs = ref([])

  // 回合倒计时（秒）
  const turnCountdown = ref(10)
  const countdownTimer = ref(null)

  // 目标选择模式
  const targetSelectionMode = ref(false)
  const pendingSkill = ref(null)
  const hoveredTarget = ref(null)

  // 我的技能列表
  const mySkills = ref([])

  // 战斗结果
  const showResult = ref(false)
  const combatResult = ref(null)

  // 自动wait标记
  const autoWaitPending = ref(false)

  // 战斗特效队列
  const effectQueue = ref([])

  // 玩家名称（用于判断己方）
  const myName = ref('')
  const myFaction = ref('')

  // 计算属性：我方角色
  const allies = computed(() => {
    if (!myFaction.value) return []
    return characters.value.filter(c => c.faction === myFaction.value)
  })

  // 计算属性：敌方角色（按阵营分组）
  const enemyFactions = computed(() => {
    if (!myFaction.value) return []
    const grouped = {}
    for (const faction of factions.value) {
      if (faction.id !== myFaction.value) {
        grouped[faction.id] = characters.value.filter(c => c.faction === faction.id)
      }
    }
    return grouped
  })

  // 计算属性：所有敌人（扁平化）
  const enemies = computed(() => {
    const result = []
    for (const factionId in enemyFactions.value) {
      result.push(...enemyFactions.value[factionId])
    }
    return result
  })

  // 计算属性：存活的敌人
  const aliveEnemies = computed(() => {
    return enemies.value.filter(e => !e.isDead)
  })

  // 计算属性：存活的己方
  const aliveAllies = computed(() => {
    return allies.value.filter(a => !a.isDead)
  })

  // 计算属性：当前行动者
  const currentActor = computed(() => {
    if (actionBar.value.length === 0) return null
    const first = actionBar.value[0]
    return characters.value.find(c => c.name === first.name)
  })

  // 获取角色
  function getCharacter(name) {
    return characters.value.find(c => c.name === name)
  }

  // 更新战斗状态
  function updateCombatState(data) {
    if (data.isInCombat !== undefined) isInCombat.value = data.isInCombat
    if (data.combatId !== undefined) combatId.value = data.combatId
    if (data.factions !== undefined) {
      factions.value = data.factions
    }
    // 先设置 myName，这样后续的 updateCharacterFactions 能正确计算 myFaction
    if (data.myName !== undefined) {
      myName.value = data.myName
    }
    if (data.characters !== undefined) {
      // 合并角色数据，使用角色自带的阵营信息
      for (const newChar of data.characters) {
        const existing = characters.value.find(c => c.name === newChar.name)
        if (existing) {
          Object.assign(existing, newChar)
        } else {
          characters.value.push(newChar)
        }
      }
      // 如果角色没有阵营信息，从factions中补充
      updateCharacterFactions()
    }
    if (data.actionBar !== undefined) actionBar.value = data.actionBar
    if (data.currentTurn !== undefined) {
      const prevTurn = currentTurn.value
      currentTurn.value = data.currentTurn
      // 当回合切换时，重新启动倒计时（无论是否是自己的回合）
      // 只要当前行动者是玩家（非AI敌人），就显示倒计时
      if (data.currentTurn && data.currentTurn !== prevTurn) {
        // 检查当前行动者是否是玩家（敌人名字通常包含#）
        const isPlayerTurn = !data.currentTurn.includes('#')
        if (isPlayerTurn) {
          startCountdown()
        } else {
          stopCountdown()
        }
      }
    }
    if (data.isMyTurn !== undefined) {
      isMyTurn.value = data.isMyTurn
    }
    if (data.mySkills !== undefined) mySkills.value = data.mySkills
  }

  // 根据参战方更新角色的阵营信息（仅当角色没有阵营时）
  function updateCharacterFactions() {
    for (const faction of factions.value) {
      for (const memberName of faction.members) {
        const char = characters.value.find(c => c.name === memberName)
        if (char && !char.faction) {
          char.faction = faction.id
        }
      }
    }
    updateMyFaction()
  }

  // 更新我的阵营
  function updateMyFaction() {
    if (!myName.value) return
    // 优先从角色数据中获取阵营
    const selfChar = characters.value.find(c => c.name === myName.value || c.isSelf)
    if (selfChar && selfChar.faction) {
      myFaction.value = selfChar.faction
      return
    }
    // 否则从factions中查找
    for (const faction of factions.value) {
      if (faction.members.includes(myName.value)) {
        myFaction.value = faction.id
        break
      }
    }
  }

  // 添加战斗日志
  function addBattleLog(log) {
    battleLogs.value.push({
      ...log,
      timestamp: Date.now()
    })
    // 保留最近100条日志
    if (battleLogs.value.length > 100) {
      battleLogs.value.shift()
    }
  }

  // 更新角色状态
  function updateCharacter(name, updates) {
    const character = characters.value.find(c => c.name === name)
    if (character) {
      Object.assign(character, updates)
    }
  }

  // 启动倒计时
  function startCountdown() {
    stopCountdown()
    turnCountdown.value = 10
    countdownTimer.value = setInterval(() => {
      if (turnCountdown.value > 0) {
        turnCountdown.value--
      } else {
        stopCountdown()
      }
    }, 1000)
  }

  // 停止倒计时
  function stopCountdown() {
    if (countdownTimer.value) {
      clearInterval(countdownTimer.value)
      countdownTimer.value = null
    }
  }

  // 进入目标选择模式
  function enterTargetSelection(skill) {
    targetSelectionMode.value = true
    pendingSkill.value = skill
  }

  // 退出目标选择模式
  function exitTargetSelection() {
    targetSelectionMode.value = false
    pendingSkill.value = null
    hoveredTarget.value = null
  }

  // 设置悬浮目标
  function setHoveredTarget(target) {
    hoveredTarget.value = target
  }

  // 添加战斗特效
  function addEffect(effect) {
    effectQueue.value.push({
      ...effect,
      id: Date.now() + Math.random(),
      timestamp: Date.now()
    })
  }

  // 移除战斗特效
  function removeEffect(effectId) {
    const index = effectQueue.value.findIndex(e => e.id === effectId)
    if (index !== -1) {
      effectQueue.value.splice(index, 1)
    }
  }

  // 清除过期特效
  function clearExpiredEffects() {
    const now = Date.now()
    effectQueue.value = effectQueue.value.filter(e => now - e.timestamp < 2000)
  }

  // 显示战斗结果
  function showCombatResult(result) {
    combatResult.value = result
    showResult.value = true
  }

  // 关闭战斗结果
  function closeCombatResult() {
    showResult.value = false
    combatResult.value = null
  }

  // 重置状态
  function reset() {
    isInCombat.value = false
    combatId.value = null
    factions.value = []
    characters.value = []
    actionBar.value = []
    currentTurn.value = null
    isMyTurn.value = false
    currentActorId.value = null
    battleLogs.value = []
    turnCountdown.value = 10
    stopCountdown()
    targetSelectionMode.value = false
    pendingSkill.value = null
    hoveredTarget.value = null
    mySkills.value = []
    showResult.value = false
    combatResult.value = null
    autoWaitPending.value = false
    effectQueue.value = []
    myName.value = ''
    myFaction.value = ''
  }

  return {
    // 状态
    isInCombat, combatId, factions, characters, actionBar,
    currentTurn, isMyTurn, currentActorId, battleLogs,
    turnCountdown, targetSelectionMode, pendingSkill, hoveredTarget,
    mySkills, showResult, combatResult, autoWaitPending, effectQueue,
    myName, myFaction,
    // 计算属性
    allies, enemies, enemyFactions, aliveEnemies, aliveAllies, currentActor,
    // 方法
    getCharacter, updateCombatState, addBattleLog, updateCharacter,
    startCountdown, stopCountdown,
    enterTargetSelection, exitTargetSelection, setHoveredTarget,
    addEffect, removeEffect, clearExpiredEffects,
    showCombatResult, closeCombatResult, reset
  }
})

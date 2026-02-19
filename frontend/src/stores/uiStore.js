import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUIStore = defineStore('ui', () => {
  // 当前打开的面板
  const activePanel = ref(null) // 'character' | 'inventory' | 'party' | 'entities' | null

  // 交互目标
  const interactionTarget = ref(null)
  const showInteractionModal = ref(false)

  // 查看角色弹窗
  const inspectCharacter = ref(null)
  const showInspectCharacterModal = ref(false)

  // 查看物品弹窗
  const inspectItem = ref(null)
  const showInspectItemModal = ref(false)

  // 通用信息弹窗（用于显示敌人、NPC等信息）
  const infoModal = ref({
    visible: false,
    title: '',
    content: ''
  })

  // 右键菜单
  const contextMenu = ref({
    visible: false,
    x: 0,
    y: 0,
    items: [],
    target: null
  })

  // 提示消息
  const toast = ref({
    visible: false,
    message: '',
    type: 'info' // 'info' | 'success' | 'warning' | 'error'
  })

  // 切换面板
  function togglePanel(panelName) {
    console.log('[UIStore] 切换面板:', panelName, '->', activePanel.value === panelName ? null : panelName)
    if (activePanel.value === panelName) {
      activePanel.value = null
    } else {
      activePanel.value = panelName
    }
  }

  // 关闭面板
  function closePanel() {
    console.log('[UIStore] 关闭面板')
    activePanel.value = null
  }

  // 打开交互弹窗
  function openInteraction(entity) {
    console.log('[UIStore] 打开交互弹窗:', entity?.name)
    interactionTarget.value = entity
    showInteractionModal.value = true
  }

  // 关闭交互弹窗
  function closeInteraction() {
    console.log('[UIStore] 关闭交互弹窗')
    interactionTarget.value = null
    showInteractionModal.value = false
  }

  // 打开查看角色弹窗
  function openInspectCharacter(characterData) {
    console.log('[UIStore] 打开查看角色弹窗:', characterData?.name)
    inspectCharacter.value = characterData
    showInspectCharacterModal.value = true
  }

  // 关闭查看角色弹窗
  function closeInspectCharacter() {
    console.log('[UIStore] 关闭查看角色弹窗')
    inspectCharacter.value = null
    showInspectCharacterModal.value = false
  }

  // 打开查看物品弹窗
  function openInspectItem(itemData) {
    console.log('[UIStore] 打开查看物品弹窗:', itemData?.name)
    inspectItem.value = itemData
    showInspectItemModal.value = true
  }

  // 关闭查看物品弹窗
  function closeInspectItem() {
    console.log('[UIStore] 关闭查看物品弹窗')
    inspectItem.value = null
    showInspectItemModal.value = false
  }

  // 打开通用信息弹窗
  function openInfoModal(title, content) {
    console.log('[UIStore] 打开信息弹窗:', title)
    infoModal.value = {
      visible: true,
      title,
      content
    }
  }

  // 关闭通用信息弹窗
  function closeInfoModal() {
    console.log('[UIStore] 关闭信息弹窗')
    infoModal.value.visible = false
  }

  // 显示右键菜单
  function showContextMenu(x, y, items, target = null) {
    console.log('[UIStore] 显示右键菜单:', { x, y, items: items.length, target: target?.name })
    contextMenu.value = {
      visible: true,
      x,
      y,
      items,
      target
    }
  }

  // 隐藏右键菜单
  function hideContextMenu() {
    contextMenu.value.visible = false
  }

  // 显示提示消息
  function showToast(message, type = 'info', duration = 3000) {
    console.log('[UIStore] 显示Toast:', type, message)
    toast.value = {
      visible: true,
      message,
      type
    }
    setTimeout(() => {
      toast.value.visible = false
    }, duration)
  }

  return {
    // 状态
    activePanel,
    interactionTarget, showInteractionModal,
    inspectCharacter, showInspectCharacterModal,
    inspectItem, showInspectItemModal,
    infoModal,
    contextMenu,
    toast,
    // 方法
    togglePanel, closePanel,
    openInteraction, closeInteraction,
    openInspectCharacter, closeInspectCharacter,
    openInspectItem, closeInspectItem,
    openInfoModal, closeInfoModal,
    showContextMenu, hideContextMenu,
    showToast
  }
})

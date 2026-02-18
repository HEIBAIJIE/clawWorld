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
    if (activePanel.value === panelName) {
      activePanel.value = null
    } else {
      activePanel.value = panelName
    }
  }

  // 关闭面板
  function closePanel() {
    activePanel.value = null
  }

  // 打开交互弹窗
  function openInteraction(entity) {
    interactionTarget.value = entity
    showInteractionModal.value = true
  }

  // 关闭交互弹窗
  function closeInteraction() {
    interactionTarget.value = null
    showInteractionModal.value = false
  }

  // 打开查看角色弹窗
  function openInspectCharacter(characterData) {
    inspectCharacter.value = characterData
    showInspectCharacterModal.value = true
  }

  // 关闭查看角色弹窗
  function closeInspectCharacter() {
    inspectCharacter.value = null
    showInspectCharacterModal.value = false
  }

  // 打开查看物品弹窗
  function openInspectItem(itemData) {
    inspectItem.value = itemData
    showInspectItemModal.value = true
  }

  // 关闭查看物品弹窗
  function closeInspectItem() {
    inspectItem.value = null
    showInspectItemModal.value = false
  }

  // 显示右键菜单
  function showContextMenu(x, y, items, target = null) {
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
    contextMenu,
    toast,
    // 方法
    togglePanel, closePanel,
    openInteraction, closeInteraction,
    openInspectCharacter, closeInspectCharacter,
    openInspectItem, closeInspectItem,
    showContextMenu, hideContextMenu,
    showToast
  }
})

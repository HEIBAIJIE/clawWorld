import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useTradeStore = defineStore('trade', () => {
  // 交易基础信息
  const isInTrade = ref(false)
  const partnerName = ref('')

  // 我方资产
  const myGold = ref(0)
  const myInventory = ref([])

  // 我方交易提供
  const myOfferGold = ref(0)
  const myOfferItems = ref([])
  const myLocked = ref(false)
  const myConfirmed = ref(false)  // 我方是否已确认交易

  // 对方交易提供
  const partnerOfferGold = ref(0)
  const partnerOfferItems = ref([])
  const partnerLocked = ref(false)

  // 计算属性：可交易的背包物品（排除已放入交易框的）
  const availableInventory = computed(() => {
    const offerItemNames = myOfferItems.value.map(item => item.name)
    return myInventory.value.filter(item => !offerItemNames.includes(item.name))
  })

  // 计算属性：双方是否都已锁定
  const bothLocked = computed(() => myLocked.value && partnerLocked.value)

  // 开始交易
  function startTrade(partner, gold, inventory) {
    isInTrade.value = true
    partnerName.value = partner
    myGold.value = gold
    myInventory.value = inventory
    myOfferGold.value = 0
    myOfferItems.value = []
    myLocked.value = false
    myConfirmed.value = false
    partnerOfferGold.value = 0
    partnerOfferItems.value = []
    partnerLocked.value = false
  }

  // 更新我方资产
  function updateMyAssets(gold, inventory) {
    myGold.value = gold
    myInventory.value = inventory
  }

  // 更新交易状态
  function updateTradeState(state) {
    if (state.myOfferGold !== undefined) myOfferGold.value = state.myOfferGold
    if (state.myOfferItems !== undefined) myOfferItems.value = state.myOfferItems
    if (state.myLocked !== undefined) myLocked.value = state.myLocked
    if (state.partnerOfferGold !== undefined) partnerOfferGold.value = state.partnerOfferGold
    if (state.partnerOfferItems !== undefined) partnerOfferItems.value = state.partnerOfferItems
    if (state.partnerLocked !== undefined) partnerLocked.value = state.partnerLocked
  }

  // 添加物品到交易框
  function addItemToOffer(item) {
    if (!myOfferItems.value.find(i => i.name === item.name)) {
      myOfferItems.value.push({ ...item })
    }
  }

  // 从交易框移除物品
  function removeItemFromOffer(itemName) {
    const index = myOfferItems.value.findIndex(i => i.name === itemName)
    if (index !== -1) {
      myOfferItems.value.splice(index, 1)
    }
  }

  // 设置我方交易金额
  function setMyOfferGold(amount) {
    myOfferGold.value = Math.max(0, Math.min(amount, myGold.value))
  }

  // 锁定/解锁
  function setMyLocked(locked) {
    myLocked.value = locked
  }

  // 设置我方已确认
  function setMyConfirmed(confirmed) {
    myConfirmed.value = confirmed
  }

  // 结束交易
  function endTrade() {
    isInTrade.value = false
    partnerName.value = ''
    myGold.value = 0
    myInventory.value = []
    myOfferGold.value = 0
    myOfferItems.value = []
    myLocked.value = false
    myConfirmed.value = false
    partnerOfferGold.value = 0
    partnerOfferItems.value = []
    partnerLocked.value = false
  }

  // 重置
  function reset() {
    endTrade()
  }

  return {
    // 状态
    isInTrade,
    partnerName,
    myGold,
    myInventory,
    myOfferGold,
    myOfferItems,
    myLocked,
    myConfirmed,
    partnerOfferGold,
    partnerOfferItems,
    partnerLocked,
    // 计算属性
    availableInventory,
    bothLocked,
    // 方法
    startTrade,
    updateMyAssets,
    updateTradeState,
    addItemToOffer,
    removeItemFromOffer,
    setMyOfferGold,
    setMyLocked,
    setMyConfirmed,
    endTrade,
    reset
  }
})

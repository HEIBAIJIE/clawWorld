import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useShopStore = defineStore('shop', () => {
  // 商店基础信息
  const isInShop = ref(false)
  const shopName = ref('')
  const purchaseInfo = ref('') // 收购信息
  const shopGold = ref(0) // 商店资金

  // 商店商品列表
  const shopItems = ref([])

  // 玩家资产（在商店中显示）
  const playerGold = ref(0)
  const playerInventory = ref([])
  const inventoryCapacity = ref(50)
  const inventoryUsed = ref(0)

  // 计算属性：可出售的背包物品
  const sellableItems = computed(() => {
    return playerInventory.value.filter(item => !item.isEquipment || item.canSell)
  })

  // 打开商店
  function openShop(name) {
    isInShop.value = true
    shopName.value = name
    shopItems.value = []
    purchaseInfo.value = ''
    shopGold.value = 0
  }

  // 更新商店商品
  function updateShopItems(items) {
    shopItems.value = items
  }

  // 更新收购信息
  function updatePurchaseInfo(info) {
    purchaseInfo.value = info
  }

  // 更新商店资金
  function updateShopGold(gold) {
    shopGold.value = gold
  }

  // 更新玩家资产
  function updatePlayerAssets(gold, inventory, used, capacity) {
    playerGold.value = gold
    if (inventory) {
      playerInventory.value = inventory
    }
    if (used !== undefined) {
      inventoryUsed.value = used
    }
    if (capacity !== undefined) {
      inventoryCapacity.value = capacity
    }
  }

  // 关闭商店
  function closeShop() {
    isInShop.value = false
    shopName.value = ''
    shopItems.value = []
    purchaseInfo.value = ''
    shopGold.value = 0
    playerGold.value = 0
    playerInventory.value = []
  }

  // 重置
  function reset() {
    closeShop()
  }

  return {
    // 状态
    isInShop,
    shopName,
    shopItems,
    purchaseInfo,
    shopGold,
    playerGold,
    playerInventory,
    inventoryCapacity,
    inventoryUsed,
    // 计算属性
    sellableItems,
    // 方法
    openShop,
    updateShopItems,
    updatePurchaseInfo,
    updateShopGold,
    updatePlayerAssets,
    closeShop,
    reset
  }
})

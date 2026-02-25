<template>
  <div class="shop-overlay" v-if="shopStore.isInShop">
    <div class="shop-window sci-panel">
      <!-- 标题栏 -->
      <div class="shop-header">
        <span class="shop-title">商店：{{ shopStore.shopName }}</span>
        <button class="shop-close sci-button" @click="handleLeaveShop">离开商店</button>
      </div>

      <!-- 商店主区域 -->
      <div class="shop-main">
        <!-- 左侧：商店商品 -->
        <div class="shop-side shop-goods">
          <div class="shop-side-header">
            <span class="side-title">出售商品</span>
          </div>

          <div class="shop-items-list">
            <div
              v-for="item in shopStore.shopItems"
              :key="item.name"
              class="shop-item"
              @click="handleBuyItem(item)"
            >
              <span class="item-icon">{{ getItemIcon(item) }}</span>
              <div class="item-info">
                <div class="item-name">{{ item.name }}</div>
                <div class="item-desc" v-if="item.description">{{ item.description }}</div>
              </div>
              <div class="item-meta">
                <div class="item-price">
                  <span class="gold-icon">💰</span>
                  <span class="price-value">{{ item.price }}</span>
                </div>
                <div class="item-stock" :class="{ low: item.stock <= 5 }">
                  库存: {{ item.stock }}
                </div>
              </div>
            </div>

            <div v-if="shopStore.shopItems.length === 0" class="empty-hint">
              商店暂无商品
            </div>
          </div>

          <!-- 收购信息 -->
          <div class="purchase-info" v-if="shopStore.purchaseInfo">
            <span class="purchase-label">收购信息：</span>
            <span class="purchase-text">{{ shopStore.purchaseInfo }}</span>
          </div>
        </div>

        <!-- 中间分隔 -->
        <div class="shop-divider"></div>

        <!-- 右侧：玩家背包 -->
        <div class="shop-side player-inventory">
          <div class="shop-side-header">
            <span class="side-title">我的背包</span>
            <span class="inventory-hint">点击物品出售</span>
          </div>

          <div class="inventory-grid">
            <div
              v-for="(slot, index) in inventorySlots"
              :key="'inv-' + index"
              class="inventory-slot"
              :class="{ empty: !slot, clickable: slot }"
              @click="handleSellItem(slot)"
            >
              <template v-if="slot">
                <span class="item-icon">{{ getItemIcon(slot) }}</span>
                <span v-if="slot.quantity > 1" class="item-count">{{ slot.quantity }}</span>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部资产显示（对称布局） -->
      <div class="shop-footer">
        <div class="footer-left">
          <span class="gold-icon">💰</span>
          <span class="gold-label">商店资金:</span>
          <span class="gold-value">{{ shopStore.shopGold }}</span>
        </div>
        <div class="footer-right">
          <span class="gold-icon">💰</span>
          <span class="gold-label">我的金币:</span>
          <span class="gold-value">{{ displayGold }}</span>
        </div>
      </div>
    </div>

    <!-- 数量输入弹窗 -->
    <div class="quantity-modal" v-if="showQuantityModal">
      <div class="quantity-modal-content sci-panel">
        <div class="quantity-header">
          {{ quantityModalType === 'buy' ? '购买' : '出售' }} {{ selectedItem?.name }}
        </div>
        <div class="quantity-body">
          <div class="quantity-info" v-if="quantityModalType === 'buy'">
            <span>单价: {{ selectedItem?.price }} 金币</span>
            <span>库存: {{ selectedItem?.stock }}</span>
          </div>
          <div class="quantity-info" v-else>
            <span>持有: {{ selectedItem?.quantity || 1 }}</span>
          </div>
          <div class="quantity-input-row">
            <label>数量:</label>
            <input
              type="number"
              class="sci-input quantity-input"
              v-model.number="inputQuantity"
              :min="1"
              :max="maxQuantity"
              @keyup.enter="confirmQuantity"
            />
          </div>
          <div class="quantity-total" v-if="quantityModalType === 'buy'">
            总价: {{ (selectedItem?.price || 0) * inputQuantity }} 金币
          </div>
        </div>
        <div class="quantity-actions">
          <button class="sci-button" @click="cancelQuantity">取消</button>
          <button class="sci-button primary" @click="confirmQuantity">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useShopStore } from '../../stores/shopStore'
import { usePlayerStore } from '../../stores/playerStore'
import { useCommand } from '../../composables/useCommand'

const shopStore = useShopStore()
const playerStore = usePlayerStore()
const { sendCommand } = useCommand()

// 数量输入弹窗状态
const showQuantityModal = ref(false)
const quantityModalType = ref('buy') // 'buy' | 'sell'
const selectedItem = ref(null)
const inputQuantity = ref(1)

// 最大可输入数量
const maxQuantity = computed(() => {
  if (!selectedItem.value) return 1
  if (quantityModalType.value === 'buy') {
    return selectedItem.value.stock || 1
  } else {
    return selectedItem.value.quantity || 1
  }
})

// 背包槽位（显示24个）- 优先使用 shopStore，备选 playerStore
const inventorySlots = computed(() => {
  const slots = new Array(24).fill(null)
  const inventory = shopStore.playerInventory.length > 0 ? shopStore.playerInventory : playerStore.inventory
  inventory.forEach((item, index) => {
    if (index < 24) slots[index] = item
  })
  return slots
})

// 显示金币 - 优先使用 shopStore，备选 playerStore
const displayGold = computed(() => {
  return shopStore.playerGold > 0 ? shopStore.playerGold : playerStore.gold
})

// 获取物品图标
function getItemIcon(item) {
  if (!item) return ''
  if (item.isEquipment) return '⚔️'
  if (item.name.includes('药水') || item.name.includes('药剂')) return '🧪'
  if (item.name.includes('技能书')) return '📖'
  return '📦'
}

// 处理购买物品
function handleBuyItem(item) {
  if (!item || item.stock <= 0) return
  selectedItem.value = item
  quantityModalType.value = 'buy'
  inputQuantity.value = 1
  showQuantityModal.value = true
}

// 处理出售物品
function handleSellItem(item) {
  if (!item) return
  selectedItem.value = item
  quantityModalType.value = 'sell'
  inputQuantity.value = 1
  showQuantityModal.value = true
}

// 确认数量
function confirmQuantity() {
  if (!selectedItem.value || inputQuantity.value < 1) return

  const quantity = Math.min(inputQuantity.value, maxQuantity.value)

  if (quantityModalType.value === 'buy') {
    sendCommand(`shop buy ${selectedItem.value.name} ${quantity}`)
  } else {
    sendCommand(`shop sell ${selectedItem.value.name} ${quantity}`)
  }

  showQuantityModal.value = false
  selectedItem.value = null
}

// 取消数量输入
function cancelQuantity() {
  showQuantityModal.value = false
  selectedItem.value = null
}

// 离开商店
function handleLeaveShop() {
  sendCommand('shop leave')
}
</script>

<style scoped>
.shop-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  z-index: 100;
}

.shop-window {
  width: 90%;
  max-width: 800px;
  min-width: 600px;
  max-height: 90%;
  display: flex;
  flex-direction: column;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
  overflow: hidden;
}

.shop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-dark);
}

.shop-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-highlight);
}

.shop-close {
  padding: 6px 12px;
  font-size: 12px;
}

.shop-main {
  display: flex;
  padding: 16px;
  gap: 16px;
  flex: 1;
  overflow: hidden;
}

.shop-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shop-goods {
  flex: 1.2;
}

.player-inventory {
  flex: 0.8;
}

.shop-side-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.side-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.inventory-hint {
  font-size: 11px;
  color: var(--text-muted);
}

.shop-items-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.shop-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--bg-dark);
  border: 1px solid var(--border-color);
  border-radius: var(--button-radius);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.shop-item:hover {
  border-color: var(--primary);
  background: var(--bg-hover);
}

.shop-item .item-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.shop-item .item-info {
  flex: 1;
  min-width: 0;
}

.shop-item .item-name {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.shop-item .item-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-item .item-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.shop-item .item-price {
  display: flex;
  align-items: center;
  gap: 4px;
}

.shop-item .gold-icon {
  font-size: 12px;
}

.shop-item .price-value {
  color: #ffd700;
  font-weight: 600;
  font-size: 13px;
}

.shop-item .item-stock {
  font-size: 11px;
  color: var(--text-muted);
}

.shop-item .item-stock.low {
  color: var(--entity-enemy);
}

.empty-hint {
  text-align: center;
  color: var(--text-muted);
  padding: 20px;
  font-size: 13px;
}

.purchase-info {
  padding: 8px 12px;
  background: var(--bg-dark);
  border-radius: var(--button-radius);
  font-size: 12px;
}

.purchase-label {
  color: var(--text-muted);
}

.purchase-text {
  color: var(--text-secondary);
}

.shop-divider {
  width: 1px;
  background: linear-gradient(180deg, transparent, var(--border-color), transparent);
}

.inventory-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  flex: 1;
  overflow-y: auto;
}

.inventory-slot {
  aspect-ratio: 1;
  background: var(--bg-dark);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  min-height: 40px;
}

.inventory-slot.clickable {
  cursor: pointer;
  transition: all var(--transition-fast);
}

.inventory-slot.clickable:hover {
  border-color: var(--primary);
  background: var(--bg-hover);
}

.inventory-slot.empty {
  opacity: 0.5;
}

.inventory-slot .item-icon {
  font-size: 18px;
}

.inventory-slot .item-count {
  position: absolute;
  bottom: 2px;
  right: 4px;
  font-size: 9px;
  color: var(--text-primary);
  text-shadow: 0 0 2px var(--bg-dark);
}

.shop-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-dark);
}

.footer-left,
.footer-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-left .gold-icon,
.footer-right .gold-icon {
  font-size: 16px;
}

.footer-left .gold-label,
.footer-right .gold-label {
  color: var(--text-muted);
  font-size: 13px;
}

.footer-left .gold-value,
.footer-right .gold-value {
  color: #ffd700;
  font-weight: 600;
  font-size: 14px;
}

/* 数量输入弹窗 */
.quantity-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  z-index: 200;
}

.quantity-modal-content {
  width: 280px;
  padding: 16px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
}

.quantity-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-highlight);
  margin-bottom: 12px;
  text-align: center;
}

.quantity-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quantity-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
}

.quantity-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quantity-input-row label {
  font-size: 13px;
  color: var(--text-primary);
}

.quantity-input {
  flex: 1;
  padding: 8px 12px;
  font-size: 14px;
  text-align: center;
}

.quantity-total {
  text-align: center;
  font-size: 13px;
  color: #ffd700;
  font-weight: 500;
}

.quantity-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.quantity-actions .sci-button {
  flex: 1;
  padding: 8px 16px;
}
</style>

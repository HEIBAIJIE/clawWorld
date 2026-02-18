<template>
  <div class="action-bar">
    <button
      class="sci-button sci-icon-button action-bar-button"
      :class="{ active: uiStore.activePanel === 'character' }"
      @click="uiStore.togglePanel('character')"
      title="角色信息 (1)"
    >
      👤
      <span class="badge" v-if="playerStore.freeAttributePoints > 0">
        {{ playerStore.freeAttributePoints }}
      </span>
    </button>

    <button
      class="sci-button sci-icon-button action-bar-button"
      :class="{ active: uiStore.activePanel === 'inventory' }"
      @click="uiStore.togglePanel('inventory')"
      title="背包 (2)"
    >
      🎒
    </button>

    <button
      class="sci-button sci-icon-button action-bar-button"
      :class="{ active: uiStore.activePanel === 'party' }"
      @click="uiStore.togglePanel('party')"
      title="队伍 (3)"
    >
      👥
    </button>

    <button
      class="sci-button sci-icon-button action-bar-button"
      :class="{ active: uiStore.activePanel === 'entities' }"
      @click="uiStore.togglePanel('entities')"
      title="实体列表 (4)"
    >
      📋
    </button>
  </div>
</template>

<script setup>
import { useUIStore } from '../../stores/uiStore'
import { usePlayerStore } from '../../stores/playerStore'

const uiStore = useUIStore()
const playerStore = usePlayerStore()
</script>

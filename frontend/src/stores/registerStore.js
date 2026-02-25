import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useRegisterStore = defineStore('register', () => {
  // 是否显示注册窗口
  const isInRegister = ref(false)

  // 可选职业列表（从服务端解析）
  const roles = ref([])

  // 选中的职业
  const selectedRole = ref(null)

  // 输入的昵称
  const nickname = ref('')

  // 打开注册窗口
  function openRegister() {
    isInRegister.value = true
    selectedRole.value = null
    nickname.value = ''
  }

  // 更新职业列表
  function updateRoles(roleList) {
    roles.value = roleList
    // 默认选中第一个职业
    if (roleList.length > 0 && !selectedRole.value) {
      selectedRole.value = roleList[0]
    }
  }

  // 选择职业
  function selectRole(role) {
    selectedRole.value = role
  }

  // 设置昵称
  function setNickname(name) {
    nickname.value = name
  }

  // 关闭注册窗口
  function closeRegister() {
    isInRegister.value = false
    roles.value = []
    selectedRole.value = null
    nickname.value = ''
  }

  // 重置
  function reset() {
    closeRegister()
  }

  return {
    // 状态
    isInRegister,
    roles,
    selectedRole,
    nickname,
    // 方法
    openRegister,
    updateRoles,
    selectRole,
    setNickname,
    closeRegister,
    reset
  }
})

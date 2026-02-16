import client from './client'

export const gameApi = {
  // 登录或注册
  login(username, password) {
    return client.post('/auth/login', { username, password })
  },

  // 登出
  logout() {
    return client.post('/auth/logout')
  },

  // 执行指令
  executeCommand(command, windowId, windowType) {
    return client.post('/command/execute', {
      command,
      windowId,
      windowType
    })
  }
}

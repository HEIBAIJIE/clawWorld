import client from './client'

export const gameApi = {
  // 登录或注册
  login(username, password) {
    return client.post('/auth/login', { username, password })
  },

  // 登出
  logout(sessionId) {
    return client.post('/auth/logout', { sessionId })
  },

  // 执行指令
  executeCommand(sessionId, command) {
    return client.post('/command/execute', {
      sessionId,
      command
    })
  }
}

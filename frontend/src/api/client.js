import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 120000, // 2分钟超时，考虑到战斗可能需要等待
  headers: {
    'Content-Type': 'application/json'
  }
})

// 响应拦截器：处理错误
client.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('sessionId')
      window.location.reload()
    }
    return Promise.reject(error)
  }
)

export default client

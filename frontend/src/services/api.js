import axios from 'axios'
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

export const session = reactive({ user: null, token: sessionStorage.getItem('campus-token') || '' })
export const http = axios.create({ baseURL: '/api', timeout: 15000 })
http.interceptors.request.use((config) => {
  if (session.token) config.headers.Authorization = `Bearer ${session.token}`
  return config
})
http.interceptors.response.use(
  (response) => response.data.data,
  (error) => {
    const message = error.response?.data?.message || '连接失败，请检查后端服务后重试'
    if (error.response?.status === 401) clearSession()
    if (!error.config?.quiet) ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)
export function saveSession(data) {
  session.token = data.token
  session.user = data.user
  sessionStorage.setItem('campus-token', data.token)
}
export function clearSession() {
  session.user = null
  session.token = ''
  sessionStorage.removeItem('campus-token')
}
export async function restoreSession() {
  if (session.token) {
    try {
      session.user = await http.get('/me', { quiet: true })
    } catch {
      clearSession()
    }
  }
}
export async function task(work) {
  try {
    return await work()
  } catch {
    return undefined
  }
}
export const money = (value) =>
  Number(value || 0)
    .toFixed(2)
    .replace(/\.00$/, '')
export const date = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '—')
export const labels = {
  PENDING: '待审核',
  REJECTED: '已驳回',
  LISTED: '在售中',
  RESERVED: '已预订',
  SOLD: '已售出',
  OFFLINE: '已下架',
  CONFIRMED: '待线下交易',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  RESOLVED: '已处理',
  ACTIVE: '正常',
  DISABLED: '已禁用'
}
export async function upload(file) {
  const data = new FormData()
  data.append('file', file)
  return (await http.post('/uploads', data)).url
}

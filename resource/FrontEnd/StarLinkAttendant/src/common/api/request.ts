import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/common/auth'
import router from '@/router'
import type { ApiResponse } from './types'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000,
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        removeToken()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res as any
  },
  (error) => {
    if (error.response?.status === 401) {
      removeToken()
      router.push('/login')
    }
    ElMessage.error(error.response?.data?.message || '网络请求失败')
    return Promise.reject(error)
  }
)

export default request

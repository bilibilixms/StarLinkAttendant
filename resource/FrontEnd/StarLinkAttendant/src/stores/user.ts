import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken, setToken, removeToken, getRefreshToken, setRefreshToken } from '@/common/auth'
import request from '@/common/api/request'
import type { LoginRequest, LoginResponse, UserInfoResponse } from '@/common/api/types'

const STORAGE_KEY = 'starlink_attendant_user'

export const useUserStore = defineStore('user', () => {
  const userId = ref<number>(0)
  const username = ref('')
  const realName = ref('')
  const position = ref('')
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const token = ref('')
  const isLoggedIn = ref(false)

  const login = async (loginData: LoginRequest) => {
    const res = await request.post<any, { data: LoginResponse }>('/api/auth/login', loginData)
    const data = res.data
    token.value = data.accessToken
    userId.value = data.userInfo.id
    username.value = data.userInfo.employeeNo
    realName.value = data.userInfo.realName
    position.value = data.userInfo.position
    isLoggedIn.value = true
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    saveToStorage()
  }

  const fetchUserInfo = async () => {
    try {
      const res = await request.get<any, { data: UserInfoResponse }>('/api/auth/info')
      const data = res.data
      userId.value = data.id
      username.value = data.employeeNo
      realName.value = data.realName
      position.value = data.position
      roles.value = data.roles || []
      permissions.value = data.permissions || []
    } catch {
      // ignore
    }
  }

  const logout = async () => {
    try {
      await request.post('/api/auth/logout')
    } catch {
      // ignore
    }
    userId.value = 0
    username.value = ''
    realName.value = ''
    position.value = ''
    roles.value = []
    permissions.value = []
    token.value = ''
    isLoggedIn.value = false
    removeToken()
    localStorage.removeItem(STORAGE_KEY)
  }

  const saveToStorage = () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      userId: userId.value,
      username: username.value,
      realName: realName.value,
      position: position.value,
      token: token.value,
      isLoggedIn: isLoggedIn.value,
    }))
  }

  const loadFromStorage = () => {
    const data = localStorage.getItem(STORAGE_KEY)
    if (data) {
      try {
        const parsed = JSON.parse(data)
        userId.value = parsed.userId || 0
        username.value = parsed.username || ''
        realName.value = parsed.realName || ''
        position.value = parsed.position || ''
        token.value = parsed.token || ''
        isLoggedIn.value = parsed.isLoggedIn || false
        if (token.value) {
          setToken(token.value)
        }
      } catch {
        logout()
      }
    }
  }

  return {
    userId,
    username,
    realName,
    position,
    roles,
    permissions,
    token,
    isLoggedIn,
    login,
    logout,
    fetchUserInfo,
    loadFromStorage,
  }
})

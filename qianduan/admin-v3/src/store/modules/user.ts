import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request } from '@/api/request'

interface UserInfo {
  username: string
  nickname: string
  dept?: string
  avatar?: string
}

const STORAGE_KEY = 'admin-v3-session'

const loadFromStorage = () => {
  try {
    const data = sessionStorage.getItem(STORAGE_KEY)
    if (data) return JSON.parse(data)
  } catch {}
  return null
}

const saveToStorage = (token: string, userInfo: UserInfo) => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token, userInfo }))
  } catch {}
}

const clearStorage = () => {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem('admin-v3-user')
  } catch {}
}

export const useUserStore = defineStore('user', () => {
  const saved = loadFromStorage()
  const token = ref<string>(saved?.token || '')
  const userInfo = ref<UserInfo>(saved?.userInfo || {
    username: '',
    nickname: '管理员'
  })

  const setToken = (val: string) => {
    token.value = val
    saveToStorage(val, userInfo.value)
  }

  const setUserInfo = (val: UserInfo) => {
    userInfo.value = val
    saveToStorage(token.value, val)
  }

  const login = async (username: string, password: string) => {
    const result: any = await request.post('/auth/login', { username, password })
    
    if (result.hasAdminAccess === false) {
      throw new Error('您没有权限登录管理后台，请联系管理员')
    }
    
    token.value = result.token
    userInfo.value = {
      username: result.username,
      nickname: result.name,
      dept: result.dept
    }
    saveToStorage(result.token, userInfo.value)
    return true
  }

  const logout = () => {
    token.value = ''
    userInfo.value = {
      username: '',
      nickname: ''
    }
    clearStorage()
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    login,
    logout
  }
})

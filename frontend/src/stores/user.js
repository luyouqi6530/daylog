import { ref } from 'vue'
import { defineStore } from 'pinia'

const TOKEN_KEY = 'daylog_token'
const USER_KEY = 'daylog_user'

function loadUserInfo() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch (e) {
    return null
  }
}

/**
 * 用户状态：token 与用户信息（刷新页面不丢失）
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(loadUserInfo())

  function setToken(value) {
    token.value = value
    localStorage.setItem(TOKEN_KEY, value)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem(USER_KEY, JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, userInfo, setToken, setUserInfo, logout }
})

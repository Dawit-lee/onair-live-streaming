import { reactive } from 'vue'

const KEY = 'onair.auth'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) || {}
  } catch {
    return {}
  }
}

/** 로그인 상태: { token, user: {id, username, nickname} } */
export const auth = reactive({ token: null, user: null, ...load() })

export function setAuth({ accessToken, user }) {
  auth.token = accessToken
  auth.user = user
  localStorage.setItem(KEY, JSON.stringify({ token: accessToken, user }))
}

export function clearAuth() {
  auth.token = null
  auth.user = null
  localStorage.removeItem(KEY)
}

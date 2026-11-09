import { auth, clearAuth } from './auth'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

/** 같은 출처의 /api 를 호출한다. 401이면 저장된 토큰을 지운다(만료/로그아웃). */
export async function api(path, { method = 'GET', body, raw, headers = {} } = {}) {
  const h = { ...headers }
  if (auth.token) h.Authorization = `Bearer ${auth.token}`
  let payload = body
  if (body !== undefined && !raw) {
    h['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const res = await fetch(`/api${path}`, { method, headers: h, body: payload })
  if (res.status === 401 && auth.token) clearAuth()
  if (!res.ok) {
    let message = `요청 실패 (${res.status})`
    try {
      message = (await res.json()).message || message
    } catch {}
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const fmtDate = (iso) => (iso ? new Date(iso).toLocaleString('ko-KR') : '-')
export const fmtWon = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`

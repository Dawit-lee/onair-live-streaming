import { reactive } from 'vue'
import { api } from './api'

/** 사이드바와 홈이 함께 쓰는 "지금 방송 중" 목록. 15초마다 새로고침. */
export const live = reactive({ items: [], loaded: false })

let timer = null
export async function refreshLive() {
  try {
    live.items = await api('/broadcasts?status=LIVE')
  } catch {}
  live.loaded = true
}
export function startLivePolling() {
  if (timer) return
  refreshLive()
  timer = setInterval(refreshLive, 15000)
}

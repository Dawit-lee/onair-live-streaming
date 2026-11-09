import { ref, onMounted, onUnmounted } from 'vue'

/** 사용자 ID로 고정 색을 정해 아바타 배경으로 쓴다. */
export const avatarStyle = (id) => ({ background: `hsl(${(Number(id) * 137) % 360} 55% 42%)` })
export const initial = (name) => (name || '?').trim().charAt(0).toUpperCase()
export const nickColor = (id) => `hsl(${(Number(id) * 137) % 360} 75% 68%)`
export const fmtCount = (n) => (n >= 10000 ? `${(n / 10000).toFixed(1)}만` : Number(n || 0).toLocaleString('ko-KR'))

export function fmtUptime(startedAt, now = Date.now()) {
  if (!startedAt) return ''
  const s = Math.max(0, Math.floor((now - new Date(startedAt)) / 1000))
  const p = (n) => String(n).padStart(2, '0')
  return `${p(Math.floor(s / 3600))}:${p(Math.floor((s % 3600) / 60))}:${p(s % 60)}`
}

export function timeAgo(iso) {
  if (!iso) return ''
  const m = Math.floor((Date.now() - new Date(iso)) / 60000)
  if (m < 1) return '방금 전'
  if (m < 60) return `${m}분 전`
  if (m < 1440) return `${Math.floor(m / 60)}시간 전`
  return `${Math.floor(m / 1440)}일 전`
}

/** 1초마다 갱신되는 현재 시각 (업타임 표시용) */
export function useNow() {
  const now = ref(Date.now())
  let t
  onMounted(() => (t = setInterval(() => (now.value = Date.now()), 1000)))
  onUnmounted(() => clearInterval(t))
  return now
}

/** 썸네일은 송출 중 30초마다 바뀌므로 30초 단위로 캐시를 무효화한다. */
export const thumbUrl = (id) => `/api/broadcasts/${id}/thumbnail?t=${Math.floor(Date.now() / 30000)}`

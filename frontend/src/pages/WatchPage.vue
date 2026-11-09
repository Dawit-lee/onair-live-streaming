<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Room, RoomEvent, Track } from 'livekit-client'
import { api, fmtDate } from '../lib/api'
import { auth } from '../lib/auth'
import CommentPanel from '../components/CommentPanel.vue'
import DonationPanel from '../components/DonationPanel.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const b = ref(null)
const error = ref('')
const videoEl = ref(null)
const audioBox = ref(null)
const connected = ref(false)
const hasVideo = ref(false)
const needsAudioClick = ref(false)
let room = null
let timer

const isOwner = computed(() => b.value && auth.user?.id === b.value.ownerId)

async function refresh() {
  try {
    b.value = await api(`/broadcasts/${id}`)
    error.value = ''
  } catch (e) {
    error.value = e.message
    return
  }
  if (b.value.status === 'LIVE' && !room) connect()
  if (b.value.status !== 'LIVE' && room) disconnect()
}

async function connect() {
  const r = new Room({ adaptiveStream: true })
  room = r
  r.on(RoomEvent.TrackSubscribed, (track) => {
    if (track.kind === Track.Kind.Video) {
      track.attach(videoEl.value)
      hasVideo.value = true
    } else if (track.kind === Track.Kind.Audio) {
      // 마이크 음성과 탭/시스템 소리가 각각 별도 트랙으로 오므로 모두 재생한다.
      audioBox.value.appendChild(track.attach())
    }
  })
  r.on(RoomEvent.TrackUnsubscribed, (track) => {
    track.detach().forEach((el) => el !== videoEl.value && el.remove())
    if (track.kind === Track.Kind.Video) hasVideo.value = false
  })
  r.on(RoomEvent.AudioPlaybackStatusChanged, () => (needsAudioClick.value = !r.canPlaybackAudio))
  r.on(RoomEvent.Disconnected, () => {
    if (room === r) {
      room = null
      connected.value = false
      hasVideo.value = false
      refresh()
    }
  })
  try {
    const conn = await api(`/broadcasts/${id}/viewer-token`, { method: 'POST' })
    await r.connect(conn.livekitUrl, conn.token)
    connected.value = true
    needsAudioClick.value = !r.canPlaybackAudio
  } catch (e) {
    if (room === r) room = null
    error.value = `방송 연결 실패: ${e.message}`
  }
}

function startAudio() {
  room?.startAudio()
}

function disconnect() {
  const r = room
  room = null
  connected.value = false
  hasVideo.value = false
  r?.disconnect()
  if (audioBox.value) audioBox.value.innerHTML = ''
}

async function remove() {
  if (!confirm('방송을 삭제할까요? 댓글, 후원 내역, 녹화도 함께 삭제됩니다.')) return
  try {
    await api(`/broadcasts/${id}`, { method: 'DELETE' })
    router.replace('/')
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 5000)
})
onUnmounted(() => {
  clearInterval(timer)
  disconnect()
})
</script>

<template>
  <p v-if="error" class="error">{{ error }}</p>
  <div v-if="b" class="layout">
    <div>
      <div class="player">
        <video ref="videoEl" autoplay playsinline />
        <div v-if="b.status !== 'LIVE'" class="overlay">
          <div>
            <h2>{{ b.status === 'ENDED' ? '방송이 종료되었습니다' : '방송 시작 전입니다' }}</h2>
            <RouterLink v-if="b.status === 'ENDED'" to="/recordings" class="muted">다시보기 목록 보기</RouterLink>
          </div>
        </div>
        <div v-else-if="!hasVideo" class="overlay"><span class="muted">방송 화면을 불러오는 중…</span></div>
        <div v-else-if="needsAudioClick" class="overlay" style="background: rgba(0, 0, 0, 0.3)">
          <button class="primary" @click="startAudio">🔊 소리 켜기</button>
        </div>
      </div>
      <div ref="audioBox" style="display: none" />
      <div class="row" style="margin-top: 8px">
        <span class="badge" :class="b.status">{{ b.status === 'LIVE' ? 'LIVE' : b.status === 'READY' ? '방송 전' : '종료' }}</span>
        <span v-if="connected" class="muted"><span class="dot" /> 실시간 시청 중</span>
        <span class="muted">{{ b.sourceType === 'CAMERA' ? '📱 모바일 카메라 방송' : b.sourceType === 'SCREEN' ? '🖥️ PC 화면 방송' : '' }}</span>
      </div>
      <h1>{{ b.title }}</h1>
      <div class="muted">{{ b.ownerNickname }} · {{ b.category || '일반' }} · 등록 {{ fmtDate(b.createdAt) }}</div>
      <p v-if="b.description" style="white-space: pre-wrap">{{ b.description }}</p>
      <div v-if="isOwner" class="row" style="margin-top: 12px">
        <RouterLink :to="`/studio/${b.id}`" class="btn primary">스튜디오 (송출 제어)</RouterLink>
        <RouterLink :to="`/broadcasts/${b.id}/edit`" class="btn">정보 수정</RouterLink>
        <button class="danger" @click="remove">삭제</button>
      </div>
    </div>
    <div class="side">
      <CommentPanel :broadcast-id="b.id" />
      <DonationPanel :broadcast="b" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Room, RoomEvent, Track } from 'livekit-client'
import { api, fmtDate, fmtWon } from '../lib/api'
import { auth } from '../lib/auth'
import { avatarStyle, initial, fmtCount, fmtUptime, thumbUrl, timeAgo, useNow } from '../lib/ui'
import ChatPanel from '../components/ChatPanel.vue'
import { useFullscreen, useStorage } from '@vueuse/core'
import { toast } from 'vue-sonner'
import { Users, Volume2, Play, Mic, Monitor, Smartphone, RectangleHorizontal, Maximize, Minimize, PanelRightClose, PanelRightOpen } from '@lucide/vue'

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
const alert = ref(null)
const recording = ref(null)
const now = useNow()
const playerEl = ref(null)
const { isFullscreen, isSupported: canFullscreen, toggle: toggleFullscreen } = useFullscreen(playerEl)
// 극장 모드와 채팅 접기는 다음 방송에서도 유지되도록 저장한다.
const theater = useStorage('onair-theater', false)
const chatOpen = useStorage('onair-chat-open', true)
let room = null
let timer
let alertQueue = []
let alertTimer = null

const isOwner = computed(() => b.value && auth.user?.id === b.value.ownerId)

async function refresh() {
  const prev = b.value?.status
  try {
    b.value = await api(`/broadcasts/${id}`)
    error.value = ''
    if (prev && prev !== b.value.status) {
      if (b.value.status === 'LIVE') toast.success('방송이 시작됐어요')
      if (b.value.status === 'ENDED') toast('방송이 종료됐어요')
    }
  } catch (e) {
    error.value = e.message
    return
  }
  if (b.value.status === 'LIVE' && !room) connect()
  if (b.value.status !== 'LIVE' && room) disconnect()
  if (b.value.status === 'ENDED' && !recording.value) {
    try {
      recording.value = (await api('/recordings')).find((r) => r.broadcastId === b.value.id) || null
    } catch {}
  }
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

/** 새 후원은 플레이어 위에 5초씩 순서대로 띄운다. */
function onDonation(d) {
  alertQueue.push(d)
  if (!alertTimer) nextAlert()
}
function nextAlert() {
  alert.value = alertQueue.shift() || null
  alertTimer = alert.value ? setTimeout(nextAlert, 5000) : null
}

async function remove() {
  if (!confirm('방송을 삭제할까요? 채팅, 후원 내역, 녹화도 함께 삭제됩니다.')) return
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
  clearTimeout(alertTimer)
  disconnect()
})
</script>

<template>
  <p v-if="error && !b" class="error page">{{ error }}</p>
  <div v-if="b" class="watch" :class="{ theater, 'no-chat': !chatOpen }">
    <div class="watch-main">
      <div ref="playerEl" class="player">
        <video ref="videoEl" autoplay playsinline />
        <template v-if="b.status !== 'LIVE'">
          <img class="poster" :src="thumbUrl(b.id)" alt="" @error="(e) => (e.target.style.display = 'none')" />
          <div class="overlay">
            <div>
              <h2>{{ b.status === 'ENDED' ? '방송이 종료되었습니다' : '방송 시작 전입니다' }}</h2>
              <p class="muted">{{ b.status === 'ENDED' ? `${timeAgo(b.endedAt)} 종료` : '방송이 시작되면 자동으로 재생됩니다.' }}</p>
              <RouterLink v-if="recording" :to="`/recordings/${recording.id}`" class="btn primary" style="margin-top: 8px"><Play :size="16" /> 다시보기</RouterLink>
            </div>
          </div>
        </template>
        <div v-else-if="!hasVideo" class="overlay"><span class="muted">방송 화면을 불러오는 중…</span></div>
        <div v-else-if="needsAudioClick" class="overlay" style="background: rgba(0, 0, 0, 0.3)">
          <button class="primary lg" @click="startAudio"><Volume2 :size="18" /> 소리 켜기</button>
        </div>
        <div v-if="b.status === 'LIVE'" class="hud">
          <span class="badge-live">LIVE</span>
          <span class="badge"><Users :size="12" /> {{ fmtCount(b.viewerCount) }}</span>
          <span class="badge">{{ fmtUptime(b.startedAt, now) }}</span>
        </div>
        <div v-if="alert" :key="alert.id" class="alert" role="status">
          <div><b>{{ alert.donorNickname }}</b>님이</div>
          <div class="amt">{{ fmtWon(alert.amount) }} 후원!</div>
          <div v-if="alert.message" class="msg">{{ alert.message }}</div>
        </div>
        <div class="player-ctrl">
          <button class="icon-btn" :aria-pressed="theater" :title="theater ? '기본 모드' : '극장 모드'" @click="theater = !theater">
            <RectangleHorizontal :size="18" />
          </button>
          <button v-if="canFullscreen" class="icon-btn" :title="isFullscreen ? '전체화면 종료' : '전체화면'" @click="toggleFullscreen">
            <Minimize v-if="isFullscreen" :size="18" /><Maximize v-else :size="18" />
          </button>
          <button class="icon-btn" :title="chatOpen ? '채팅 접기' : '채팅 펼치기'" @click="chatOpen = !chatOpen">
            <PanelRightClose v-if="chatOpen" :size="18" /><PanelRightOpen v-else :size="18" />
          </button>
        </div>
      </div>
      <div ref="audioBox" style="display: none" />

      <div class="watch-info">
        <h1 class="title">{{ b.title }}</h1>
        <div class="channel-row">
          <div class="avatar lg" :class="{ live: b.status === 'LIVE' }" :style="avatarStyle(b.ownerId)">{{ initial(b.ownerNickname) }}</div>
          <div class="who">
            <div class="nick">{{ b.ownerNickname }}</div>
            <div class="stats">
              <span class="tag">{{ b.category || '일반' }}</span>
              <span v-if="b.sourceType === 'CAMERA'" class="ico"><Smartphone :size="14" /> 모바일 카메라 방송</span>
              <span v-else-if="b.sourceType === 'SCREEN'" class="ico"><Monitor :size="14" /> PC 화면 방송</span>
              <template v-if="b.status === 'LIVE'">
                <span class="on">● {{ fmtCount(b.viewerCount) }}명 시청 중</span>
                <span>{{ fmtUptime(b.startedAt, now) }} 스트리밍 중</span>
              </template>
              <span v-else-if="b.status === 'ENDED'">{{ fmtDate(b.endedAt) }} 종료</span>
              <span v-else>방송 예정</span>
            </div>
          </div>
          <div v-if="isOwner" class="row">
            <RouterLink :to="`/studio/${b.id}`" class="btn primary"><Mic :size="16" /> 스튜디오</RouterLink>
            <RouterLink :to="`/broadcasts/${b.id}/edit`" class="btn">정보 수정</RouterLink>
            <button class="ghost" @click="remove">삭제</button>
          </div>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div v-if="b.description" class="desc">{{ b.description }}</div>
      </div>
    </div>
    <ChatPanel v-show="chatOpen" :broadcast="b" @donation="onDonation" />
  </div>
</template>

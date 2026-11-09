<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Room,
  RoomEvent,
  Track,
  VideoPresets,
  ScreenSharePresets,
  createLocalScreenTracks,
  createLocalVideoTrack,
  createLocalAudioTrack,
} from 'livekit-client'
import { api } from '../lib/api'
import { auth } from '../lib/auth'
import { canRecord, startRecording } from '../lib/recorder'
import CommentPanel from '../components/CommentPanel.vue'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const b = ref(null)
const error = ref('')
const info = ref('')
const previewEl = ref(null)
const canScreen = !!navigator.mediaDevices?.getDisplayMedia
const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) || !canScreen
const source = ref(isMobile ? 'CAMERA' : 'SCREEN')
const facing = ref('user')
const prepared = ref(false)
const live = ref(false)
const busy = ref(false)
const micMuted = ref(false)
const record = ref(canRecord())
const recording = ref(false)
const levels = ref({ mic: 0, screen: 0 })
const hasScreenAudio = ref(false)
const viewerCount = ref(0)

let tracks = { video: null, screenAudio: null, mic: null }
let room = null
let recorder = null
let meterCtx = null
let meterRaf = 0

const isOwner = computed(() => b.value && auth.user?.id === b.value.ownerId)

function stopTracks() {
  Object.values(tracks).forEach((t) => t?.stop())
  tracks = { video: null, screenAudio: null, mic: null }
  prepared.value = false
  hasScreenAudio.value = false
  stopMeters()
}

/** 송출 전 미리보기: 화면 공유(+탭/시스템 소리) 또는 카메라, 그리고 마이크 */
async function prepare() {
  error.value = ''
  info.value = ''
  stopTracks()
  try {
    if (source.value === 'SCREEN') {
      const screen = await createLocalScreenTracks({
        audio: true,
        systemAudio: 'include',
        selfBrowserSurface: 'exclude',
        surfaceSwitching: 'include',
        resolution: ScreenSharePresets.h1080fps30.resolution,
      })
      tracks.video = screen.find((t) => t.kind === Track.Kind.Video)
      tracks.screenAudio = screen.find((t) => t.kind === Track.Kind.Audio) || null
      hasScreenAudio.value = !!tracks.screenAudio
      if (!tracks.screenAudio) {
        info.value = '탭/시스템 소리가 선택되지 않았습니다. 공유 창에서 "탭 오디오 공유" 또는 "시스템 오디오 공유"를 켜면 함께 송출됩니다.'
      }
      // 브라우저의 "공유 중지" 버튼을 누르면 방송도 종료한다.
      tracks.video.mediaStreamTrack.addEventListener('ended', () => (live.value ? end() : stopTracks()))
    } else {
      tracks.video = await createLocalVideoTrack({ facingMode: facing.value, resolution: VideoPresets.h720.resolution })
    }
    tracks.mic = await createLocalAudioTrack({ echoCancellation: true, noiseSuppression: true })
    tracks.video.attach(previewEl.value)
    prepared.value = true
    startMeters()
  } catch (e) {
    stopTracks()
    error.value =
      e.name === 'NotAllowedError' ? '카메라/마이크/화면 공유 권한이 거부되었습니다.' : `장치를 열 수 없습니다: ${e.message}`
  }
}

function startMeters() {
  meterCtx = new AudioContext()
  const analyser = (track) => {
    if (!track) return null
    const a = meterCtx.createAnalyser()
    a.fftSize = 512
    meterCtx.createMediaStreamSource(new MediaStream([track.mediaStreamTrack])).connect(a)
    return a
  }
  const nodes = { mic: analyser(tracks.mic), screen: analyser(tracks.screenAudio) }
  const buf = new Uint8Array(512)
  const level = (a) => {
    if (!a) return 0
    a.getByteTimeDomainData(buf)
    let peak = 0
    for (const v of buf) peak = Math.max(peak, Math.abs(v - 128))
    return Math.min(100, (peak / 128) * 160)
  }
  const tick = () => {
    levels.value = { mic: level(nodes.mic), screen: level(nodes.screen) }
    meterRaf = requestAnimationFrame(tick)
  }
  tick()
}

function stopMeters() {
  cancelAnimationFrame(meterRaf)
  meterCtx?.close()
  meterCtx = null
  levels.value = { mic: 0, screen: 0 }
}

async function start() {
  busy.value = true
  error.value = ''
  try {
    const conn = await api(`/broadcasts/${id}/start`, { method: 'POST', body: { sourceType: source.value } })
    room = new Room({ dynacast: true })
    room.on(RoomEvent.Disconnected, () => {
      if (live.value) {
        error.value = '송출 연결이 끊겼습니다.'
        cleanupLive()
        load()
      }
    })
    const updateViewers = () => (viewerCount.value = room ? room.numParticipants - 1 : 0)
    room.on(RoomEvent.ParticipantConnected, updateViewers)
    room.on(RoomEvent.ParticipantDisconnected, updateViewers)
    await room.connect(conn.livekitUrl, conn.token)
    const lp = room.localParticipant
    await lp.publishTrack(tracks.video, {
      source: source.value === 'SCREEN' ? Track.Source.ScreenShare : Track.Source.Camera,
      simulcast: source.value === 'CAMERA',
    })
    if (tracks.screenAudio) await lp.publishTrack(tracks.screenAudio, { source: Track.Source.ScreenShareAudio })
    await lp.publishTrack(tracks.mic, { source: Track.Source.Microphone })
    live.value = true
    await load()

    if (record.value) {
      try {
        const audio = [tracks.mic, tracks.screenAudio].filter(Boolean).map((t) => t.mediaStreamTrack)
        recorder = await startRecording(id, tracks.video.mediaStreamTrack, audio)
        recording.value = true
      } catch (e) {
        info.value = `녹화를 시작하지 못했습니다 (방송은 계속됩니다): ${e.message}`
      }
    }
  } catch (e) {
    error.value = `방송 시작 실패: ${e.message}`
    // 서버에서는 LIVE가 됐는데 연결에 실패했다면 상태를 되돌린다.
    if (room) await api(`/broadcasts/${id}/end`, { method: 'POST' }).catch(() => {})
    cleanupLive()
    await load()
  } finally {
    busy.value = false
  }
}

async function end() {
  if (!live.value) return
  busy.value = true
  try {
    if (recorder) {
      info.value = '녹화 파일을 마무리하는 중…'
      try {
        await recorder.stop()
      } catch (e) {
        error.value = `녹화 저장 실패: ${e.message}`
      }
    }
    await api(`/broadcasts/${id}/end`, { method: 'POST' })
    cleanupLive()
    stopTracks()
    router.push(`/broadcasts/${id}`)
  } catch (e) {
    error.value = `종료 실패: ${e.message}`
  } finally {
    busy.value = false
  }
}

/** 이전 세션에서 LIVE로 남아 있는 방송을 강제로 종료 */
async function forceEnd() {
  try {
    await api(`/broadcasts/${id}/end`, { method: 'POST' })
    await load()
  } catch (e) {
    error.value = e.message
  }
}

function cleanupLive() {
  live.value = false
  recording.value = false
  recorder = null
  const r = room
  room = null
  r?.disconnect()
}

function toggleMic() {
  if (!tracks.mic) return
  micMuted.value ? tracks.mic.unmute() : tracks.mic.mute()
  micMuted.value = !micMuted.value
}

async function load() {
  try {
    b.value = await api(`/broadcasts/${id}`)
  } catch (e) {
    error.value = e.message
  }
}

function beforeUnload(e) {
  if (live.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(() => {
  load()
  window.addEventListener('beforeunload', beforeUnload)
})
onUnmounted(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  // 페이지를 떠나면 송출을 끊는다. 서버는 LiveKit 웹훅(송출자 퇴장)으로 방송을 종료 처리한다.
  if (live.value) {
    recorder?.stop().catch(() => {})
    api(`/broadcasts/${id}/end`, { method: 'POST' }).catch(() => {})
  }
  cleanupLive()
  stopTracks()
})
</script>

<template>
  <p v-if="b && !isOwner" class="error">이 방송의 소유자만 송출할 수 있습니다.</p>
  <div v-else-if="b" class="layout">
    <div>
      <div class="player">
        <video ref="previewEl" autoplay playsinline muted :style="{ transform: source === 'CAMERA' && facing === 'user' ? 'scaleX(-1)' : '' }" />
        <div v-if="!prepared" class="overlay">
          <div>
            <h2>송출 소스를 선택하고 미리보기를 켜세요</h2>
            <p class="muted">PC: 화면 공유 + 탭/시스템 소리 + 마이크 · 모바일: 카메라 + 마이크</p>
          </div>
        </div>
      </div>

      <div class="row" style="margin-top: 10px">
        <span v-if="live" class="badge LIVE"><span class="dot" style="background: #fff" /> LIVE</span>
        <span v-if="live" class="muted">시청자 {{ viewerCount }}명</span>
        <span v-if="recording" class="muted">● 녹화 중</span>
      </div>
      <h1>{{ b.title }}</h1>

      <div class="card" style="margin-top: 12px">
        <div class="row">
          <button :class="{ primary: source === 'SCREEN' }" :disabled="live || !canScreen" @click="source = 'SCREEN'; stopTracks()">
            🖥️ PC 화면 공유
          </button>
          <button :class="{ primary: source === 'CAMERA' }" :disabled="live" @click="source = 'CAMERA'; stopTracks()">📱 카메라</button>
          <select v-if="source === 'CAMERA'" v-model="facing" :disabled="live" style="width: auto" @change="prepared && prepare()">
            <option value="user">전면 카메라</option>
            <option value="environment">후면 카메라</option>
          </select>
        </div>
        <p v-if="!canScreen" class="muted">이 기기/브라우저는 화면 공유를 지원하지 않아 카메라 방송만 가능합니다.</p>

        <div v-if="prepared" style="margin-top: 12px">
          <div class="muted">🎙️ 마이크 {{ micMuted ? '(음소거)' : '' }}</div>
          <div class="meter"><i :style="{ width: levels.mic + '%' }" /></div>
          <template v-if="source === 'SCREEN'">
            <div class="muted" style="margin-top: 6px">🔊 탭/시스템 소리 {{ hasScreenAudio ? '' : '(없음)' }}</div>
            <div class="meter"><i :style="{ width: levels.screen + '%' }" /></div>
          </template>
        </div>

        <label v-if="canRecord()" class="row" style="color: var(--text)">
          <input v-model="record" type="checkbox" :disabled="live" style="width: auto" /> 방송을 녹화해 다시보기로 저장
        </label>

        <div class="row" style="margin-top: 12px">
          <template v-if="!live">
            <button :disabled="busy" @click="prepare">{{ prepared ? '소스 다시 선택' : '미리보기 켜기' }}</button>
            <button class="primary" :disabled="!prepared || busy || b.status === 'LIVE'" @click="start">● 방송 시작</button>
          </template>
          <template v-else>
            <button @click="toggleMic">{{ micMuted ? '마이크 켜기' : '마이크 끄기' }}</button>
            <button class="danger" :disabled="busy" @click="end">■ 방송 종료</button>
          </template>
          <RouterLink :to="`/broadcasts/${b.id}`" class="btn ghost">시청 화면</RouterLink>
        </div>
        <p v-if="b.status === 'LIVE' && !live" class="error">
          이전 송출이 아직 방송 중으로 남아 있습니다. <button @click="forceEnd">이전 방송 종료</button>
        </p>
        <p v-if="info" class="muted">{{ info }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </div>
    </div>
    <div class="side">
      <CommentPanel :broadcast-id="b.id" />
    </div>
  </div>
  <p v-else-if="error" class="error">{{ error }}</p>
</template>

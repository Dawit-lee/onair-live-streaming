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
import { api, fmtWon } from '../lib/api'
import { auth } from '../lib/auth'
import { canRecord, startRecording } from '../lib/recorder'
import { fmtCount, fmtUptime, useNow } from '../lib/ui'
import ChatPanel from '../components/ChatPanel.vue'
import { toast } from 'vue-sonner'
import { Users, Mic, MicOff, Monitor, Smartphone, Radio, Square, Volume2 } from '@lucide/vue'

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
const alert = ref(null)
const now = useNow()

let tracks = { video: null, screenAudio: null, mic: null }
let room = null
let recorder = null
let meterCtx = null
let meterRaf = 0
let pollTimer = null
let thumbTimer = null
let alertTimer = null

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
    await room.connect(conn.livekitUrl, conn.token)
    const lp = room.localParticipant
    await lp.publishTrack(tracks.video, {
      source: source.value === 'SCREEN' ? Track.Source.ScreenShare : Track.Source.Camera,
      simulcast: source.value === 'CAMERA',
    })
    if (tracks.screenAudio) await lp.publishTrack(tracks.screenAudio, { source: Track.Source.ScreenShareAudio })
    await lp.publishTrack(tracks.mic, { source: Track.Source.Microphone })
    live.value = true
    toast.success('방송을 시작했어요')
    await load()
    // 시청자 수 갱신과 목록용 라이브 썸네일(30초마다)
    pollTimer = setInterval(load, 10000)
    setTimeout(uploadThumbnail, 2000)
    thumbTimer = setInterval(uploadThumbnail, 30000)

    if (record.value) {
      try {
        const audio = [tracks.mic, tracks.screenAudio].filter(Boolean).map((t) => t.mediaStreamTrack)
        recorder = await startRecording(id, tracks.video.mediaStreamTrack, audio)
        recording.value = true
      } catch (e) {
        toast.warning(`녹화를 시작하지 못했습니다 (방송은 계속됩니다): ${e.message}`)
      }
    }
  } catch (e) {
    toast.error(`방송 시작 실패: ${e.message}`)
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
        toast.error(`녹화 저장 실패: ${e.message}`)
      }
    }
    await api(`/broadcasts/${id}/end`, { method: 'POST' })
    cleanupLive()
    stopTracks()
    toast('방송을 종료했어요')
    router.push(`/broadcasts/${id}`)
  } catch (e) {
    toast.error(`종료 실패: ${e.message}`)
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

/** 미리보기 화면을 640x360 JPEG로 캡처해 올린다. 실패해도 방송에는 영향 없음. */
function uploadThumbnail() {
  const v = previewEl.value
  if (!v || !v.videoWidth) return
  const c = document.createElement('canvas')
  c.width = 640
  c.height = 360
  const g = c.getContext('2d')
  const scale = Math.max(640 / v.videoWidth, 360 / v.videoHeight)
  const w = v.videoWidth * scale
  const h = v.videoHeight * scale
  g.drawImage(v, (640 - w) / 2, (360 - h) / 2, w, h)
  c.toBlob(
    (blob) =>
      blob &&
      fetch(`/api/broadcasts/${id}/thumbnail`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'image/jpeg' },
        body: blob,
      }).catch(() => {}),
    'image/jpeg',
    0.75,
  )
}

function onDonation(d) {
  clearTimeout(alertTimer)
  alert.value = d
  alertTimer = setTimeout(() => (alert.value = null), 5000)
}

function cleanupLive() {
  clearInterval(pollTimer)
  clearInterval(thumbTimer)
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
  clearTimeout(alertTimer)
})
</script>

<template>
  <p v-if="b && !isOwner" class="error page">이 방송의 소유자만 송출할 수 있습니다.</p>
  <div v-else-if="b" class="watch">
    <div class="watch-main">
      <div class="player">
        <video ref="previewEl" autoplay playsinline muted :style="{ transform: source === 'CAMERA' && facing === 'user' ? 'scaleX(-1)' : '' }" />
        <div v-if="!prepared" class="overlay">
          <div>
            <h2>송출 소스를 고르고 미리보기를 켜세요</h2>
            <p class="muted">PC: 화면 공유 + 탭/시스템 소리 + 마이크 · 모바일: 카메라 + 마이크</p>
          </div>
        </div>
        <div class="hud">
          <span v-if="live" class="badge-live">LIVE</span>
          <span v-else class="badge">미리보기</span>
          <span v-if="live" class="badge"><Users :size="12" /> {{ fmtCount(b.viewerCount) }}</span>
          <span v-if="live" class="badge">{{ fmtUptime(b.startedAt, now) }}</span>
          <span v-if="recording" class="badge">● REC</span>
        </div>
        <div v-if="alert" :key="alert.id" class="alert" role="status">
          <div><b>{{ alert.donorNickname }}</b>님이</div>
          <div class="amt">{{ fmtWon(alert.amount) }} 후원!</div>
          <div v-if="alert.message" class="msg">{{ alert.message }}</div>
        </div>
      </div>

      <div class="studio-panel">
        <div class="row" style="justify-content: space-between">
          <div>
            <div class="muted ico"><Radio :size="14" /> 방송 스튜디오</div>
            <h1>{{ b.title }}</h1>
          </div>
          <RouterLink :to="`/broadcasts/${b.id}/edit`" class="btn ghost">방송 정보 수정</RouterLink>
        </div>

        <div v-if="live" class="studio-stats">
          <div><span class="muted">시청자</span><b>{{ fmtCount(b.viewerCount) }}명</b></div>
          <div><span class="muted">방송 시간</span><b>{{ fmtUptime(b.startedAt, now) }}</b></div>
          <div><span class="muted">송출 소스</span><b class="ico"><Monitor v-if="source === 'SCREEN'" :size="18" /><Smartphone v-else :size="18" />{{ source === 'SCREEN' ? '화면' : '카메라' }}</b></div>
        </div>

        <div v-else class="source-pick" style="margin-top: 16px">
          <button :class="{ on: source === 'SCREEN' }" :disabled="!canScreen" @click="source = 'SCREEN'; stopTracks()">
            <span class="t ico"><Monitor :size="18" /> PC 화면 공유</span>
            <span class="d">{{ canScreen ? '화면 + 탭/시스템 소리 + 마이크' : '이 기기에서는 지원하지 않아요' }}</span>
          </button>
          <button :class="{ on: source === 'CAMERA' }" @click="source = 'CAMERA'; stopTracks()">
            <span class="t ico"><Smartphone :size="18" /> 카메라</span>
            <span class="d">휴대폰/웹캠 카메라 + 마이크</span>
          </button>
        </div>
        <select v-if="source === 'CAMERA' && !live" v-model="facing" style="margin-top: 10px" @change="prepared && prepare()">
          <option value="user">전면 카메라</option>
          <option value="environment">후면 카메라</option>
        </select>

        <div v-if="prepared" style="margin-top: 16px">
          <div class="muted ico"><Mic :size="14" /> 마이크 {{ micMuted ? '(음소거)' : '' }}</div>
          <div class="meter"><i :style="{ width: levels.mic + '%' }" /></div>
          <template v-if="source === 'SCREEN'">
            <div class="muted ico" style="margin-top: 8px"><Volume2 :size="14" /> 탭/시스템 소리 {{ hasScreenAudio ? '' : '(없음)' }}</div>
            <div class="meter"><i :style="{ width: levels.screen + '%' }" /></div>
          </template>
        </div>

        <label v-if="canRecord()" class="check">
          <input v-model="record" type="checkbox" :disabled="live" /> 방송을 녹화해 다시보기로 저장
        </label>

        <div class="row" style="margin-top: 18px">
          <template v-if="!live">
            <button class="lg" :disabled="busy" @click="prepare">{{ prepared ? '소스 다시 선택' : '미리보기 켜기' }}</button>
            <button class="primary lg" :disabled="!prepared || busy || b.status === 'LIVE'" @click="start"><Radio :size="18" /> 방송 시작</button>
          </template>
          <template v-else>
            <button class="lg" @click="toggleMic"><Mic v-if="micMuted" :size="18" /><MicOff v-else :size="18" />{{ micMuted ? '마이크 켜기' : '마이크 끄기' }}</button>
            <button class="danger lg" :disabled="busy" @click="end"><Square :size="16" /> 방송 종료</button>
          </template>
          <RouterLink :to="`/broadcasts/${b.id}`" class="btn ghost lg">시청 화면 보기</RouterLink>
        </div>
        <p v-if="b.status === 'LIVE' && !live" class="error">
          이전 송출이 아직 방송 중으로 남아 있습니다. <button style="height: 28px" @click="forceEnd">이전 방송 종료</button>
        </p>
        <p v-if="info" class="muted">{{ info }}</p>
        <p v-if="error" class="error">{{ error }}</p>
      </div>
    </div>
    <ChatPanel :broadcast="b" @donation="onDonation" />
  </div>
  <p v-else-if="error" class="error page">{{ error }}</p>
</template>

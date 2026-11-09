import { api } from './api'
import { auth } from './auth'

const TYPES = ['video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4;codecs=avc1,mp4a.40.2', 'video/mp4']

export const canRecord = () => typeof MediaRecorder !== 'undefined' && TYPES.some((t) => MediaRecorder.isTypeSupported(t))

async function upload(recordingId, seq, blob) {
  for (let attempt = 0; ; attempt++) {
    try {
      const res = await fetch(`/api/recordings/${recordingId}/chunks?seq=${seq}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/octet-stream' },
        body: blob,
      })
      if (res.ok) return
      if (res.status < 500 || attempt >= 2) throw new Error(`녹화 업로드 실패 (${res.status})`)
    } catch (e) {
      if (attempt >= 2) throw e
    }
    await new Promise((r) => setTimeout(r, 1000 * (attempt + 1)))
  }
}

/**
 * 송출 중인 영상 + (마이크 + 탭/시스템 소리를 섞은) 오디오를 MediaRecorder로 녹화하고
 * 3초 조각을 순서대로 서버에 올린다. 서버는 조각을 이어 붙여 하나의 파일로 저장한다.
 */
export async function startRecording(broadcastId, videoTrack, audioTracks) {
  const mimeType = TYPES.find((t) => MediaRecorder.isTypeSupported(t))
  const ctx = new AudioContext()
  const dest = ctx.createMediaStreamDestination()
  audioTracks.forEach((t) => ctx.createMediaStreamSource(new MediaStream([t])).connect(dest))
  const stream = new MediaStream([videoTrack, ...dest.stream.getAudioTracks()])

  const rec = await api(`/broadcasts/${broadcastId}/recordings`, { method: 'POST', body: { contentType: mimeType } })
  const mr = new MediaRecorder(stream, { mimeType, videoBitsPerSecond: 2_500_000 })
  let seq = 0
  let chain = Promise.resolve()
  const state = { error: null }

  mr.ondataavailable = (e) => {
    if (!e.data.size || state.error) return
    const n = seq++
    chain = chain.then(() => (state.error ? null : upload(rec.id, n, e.data))).catch((err) => (state.error = err))
  }
  mr.start(3000)

  return {
    state,
    async stop() {
      if (mr.state !== 'inactive') {
        await new Promise((resolve) => {
          mr.onstop = resolve
          mr.stop()
        })
      }
      await chain
      ctx.close()
      if (seq > 0) await api(`/recordings/${rec.id}/complete`, { method: 'POST' })
    },
  }
}

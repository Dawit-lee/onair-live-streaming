<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, fmtDate } from '../lib/api'
import { auth } from '../lib/auth'

const route = useRoute()
const router = useRouter()
const r = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    r.value = await api(`/recordings/${route.params.id}`)
  } catch (e) {
    error.value = e.message
  }
})

/**
 * 조각을 이어 붙인 WebM은 헤더에 길이가 없어 duration이 Infinity로 나온다.
 * 끝으로 한 번 탐색하면 브라우저가 실제 길이를 계산하므로, 그 뒤 처음으로 되돌린다.
 */
function fixDuration(e) {
  const v = e.target
  if (v.duration !== Infinity) return
  v.addEventListener('durationchange', () => (v.currentTime = 0), { once: true })
  v.currentTime = 1e101
}

async function remove() {
  if (!confirm('녹화 영상을 삭제할까요?')) return
  try {
    await api(`/recordings/${r.value.id}`, { method: 'DELETE' })
    router.replace('/recordings')
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="page" style="max-width: 1200px">
  <p v-if="error" class="error">{{ error }}</p>
  <div v-if="r">
    <div class="vod-player">
      <video :src="r.videoUrl" controls playsinline autoplay @loadedmetadata="fixDuration" />
    </div>
    <h1 style="margin-top: 16px">{{ r.title }}</h1>
    <div class="muted">
      {{ r.ownerNickname }} · 녹화 {{ fmtDate(r.startedAt) }} ~ {{ fmtDate(r.endedAt) }} · {{ (r.sizeBytes / 1048576).toFixed(1) }}MB
    </div>
    <div class="row" style="margin-top: 12px">
      <RouterLink :to="`/broadcasts/${r.broadcastId}`" class="btn">원본 방송 페이지</RouterLink>
      <button v-if="auth.user?.id === r.ownerId" class="danger" @click="remove">삭제</button>
    </div>
  </div>
  </div>
</template>

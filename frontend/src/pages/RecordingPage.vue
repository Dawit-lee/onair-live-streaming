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
  <p v-if="error" class="error">{{ error }}</p>
  <div v-if="r">
    <div class="player">
      <video :src="r.videoUrl" controls playsinline />
    </div>
    <h1>{{ r.title }}</h1>
    <div class="muted">
      {{ r.ownerNickname }} · 녹화 {{ fmtDate(r.startedAt) }} ~ {{ fmtDate(r.endedAt) }} · {{ (r.sizeBytes / 1048576).toFixed(1) }}MB
    </div>
    <div class="row" style="margin-top: 12px">
      <RouterLink :to="`/broadcasts/${r.broadcastId}`" class="btn">원본 방송 페이지</RouterLink>
      <button v-if="auth.user?.id === r.ownerId" class="danger" @click="remove">삭제</button>
    </div>
    <p class="muted" style="font-size: 12px">
      WebM 녹화는 브라우저 특성상 탐색 바 길이가 늦게 표시될 수 있습니다.
    </p>
  </div>
</template>

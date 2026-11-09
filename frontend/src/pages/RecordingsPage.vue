<script setup>
import { ref, onMounted } from 'vue'
import { api, fmtDate } from '../lib/api'

const items = ref([])
const error = ref('')
const fmtDur = (r) => {
  const s = Math.round((new Date(r.endedAt) - new Date(r.startedAt)) / 1000)
  return `${Math.floor(s / 60)}분 ${s % 60}초`
}
onMounted(async () => {
  try {
    items.value = await api('/recordings')
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <h1>다시보기</h1>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-else-if="!items.length" class="muted">저장된 녹화 영상이 없습니다.</p>
  <div class="grid">
    <RouterLink v-for="r in items" :key="r.id" :to="`/recordings/${r.id}`">
      <div class="thumb">▶ {{ fmtDur(r) }}</div>
      <h2 style="margin-top: 10px">{{ r.title }}</h2>
      <div class="muted">{{ r.ownerNickname }} · {{ fmtDate(r.startedAt) }}</div>
    </RouterLink>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../lib/api'
import { avatarStyle, initial, timeAgo } from '../lib/ui'

const items = ref([])
const error = ref('')
const loaded = ref(false)
const broken = ref({})
const fmtDur = (r) => {
  const s = Math.round((new Date(r.endedAt) - new Date(r.startedAt)) / 1000)
  const p = (n) => String(n).padStart(2, '0')
  return s >= 3600 ? `${Math.floor(s / 3600)}:${p(Math.floor((s % 3600) / 60))}:${p(s % 60)}` : `${Math.floor(s / 60)}:${p(s % 60)}`
}
onMounted(async () => {
  try {
    items.value = await api('/recordings')
  } catch (e) {
    error.value = e.message
  }
  loaded.value = true
})
</script>

<template>
  <div class="page">
    <div class="section-head"><h2>🎞️ 다시보기</h2><span class="count">{{ items.length }}</span></div>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-else-if="loaded && !items.length" class="empty">저장된 녹화 영상이 없어요. 스튜디오에서 「녹화」를 켜고 방송하면 여기에 쌓여요.</div>
    <div class="grid">
      <RouterLink v-for="r in items" :key="r.id" :to="`/recordings/${r.id}`" class="bcard">
        <div class="thumb">
          <img v-if="!broken[r.id]" :src="`/api/broadcasts/${r.broadcastId}/thumbnail`" alt="" @error="broken[r.id] = true" />
          <div v-else class="ph" :style="avatarStyle(r.ownerId)">▶</div>
          <div class="tl"><span class="badge">다시보기</span></div>
          <div class="br"><span class="badge">{{ fmtDur(r) }}</span></div>
        </div>
        <div class="info">
          <div class="avatar" :style="avatarStyle(r.ownerId)">{{ initial(r.ownerNickname) }}</div>
          <div style="min-width: 0">
            <div class="title">{{ r.title }}</div>
            <div class="owner">{{ r.ownerNickname }} · {{ timeAgo(r.startedAt) }}</div>
          </div>
        </div>
      </RouterLink>
    </div>
  </div>
</template>

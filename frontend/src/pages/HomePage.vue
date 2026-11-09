<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../lib/api'
import { auth } from '../lib/auth'
import BroadcastCard from '../components/BroadcastCard.vue'

const route = useRoute()
const items = ref([])
const error = ref('')
const loaded = ref(false)
let timer

async function load() {
  try {
    items.value = await api('/broadcasts')
    error.value = ''
  } catch (e) {
    error.value = e.message
  }
  loaded.value = true
}

const q = computed(() => (route.query.q || '').toString().toLowerCase())
const match = (b) =>
  !q.value || [b.title, b.ownerNickname, b.category, b.description].some((v) => (v || '').toLowerCase().includes(q.value))
const filtered = computed(() => items.value.filter(match))
const liveNow = computed(() => filtered.value.filter((b) => b.status === 'LIVE').sort((a, b) => b.viewerCount - a.viewerCount))
const upcoming = computed(() => filtered.value.filter((b) => b.status === 'READY'))
const ended = computed(() => filtered.value.filter((b) => b.status === 'ENDED').sort((a, b) => new Date(b.endedAt) - new Date(a.endedAt)))

watch(q, () => window.scrollTo(0, 0))
onMounted(() => {
  load()
  timer = setInterval(load, 10000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="page">
    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="q" class="section-head"><h2>‘{{ route.query.q }}’ 검색 결과</h2><span class="count">{{ filtered.length }}개</span></div>

    <section class="section">
      <div class="section-head"><h2><span class="live-dot" /> 지금 라이브</h2><span class="count">{{ liveNow.length }}</span></div>
      <div v-if="liveNow.length" class="grid">
        <BroadcastCard v-for="b in liveNow" :key="b.id" :b="b" />
      </div>
      <div v-else-if="loaded" class="empty">
        지금 방송 중인 채널이 없어요.
        <div v-if="auth.token" style="margin-top: 12px"><RouterLink to="/broadcasts/new" class="btn primary">● 첫 방송 시작하기</RouterLink></div>
      </div>
    </section>

    <section v-if="upcoming.length" class="section">
      <div class="section-head"><h2>방송 예정</h2><span class="count">{{ upcoming.length }}</span></div>
      <div class="grid"><BroadcastCard v-for="b in upcoming" :key="b.id" :b="b" /></div>
    </section>

    <section v-if="ended.length" class="section">
      <div class="section-head"><h2>지난 방송</h2><span class="count">{{ ended.length }}</span></div>
      <div class="grid"><BroadcastCard v-for="b in ended" :key="b.id" :b="b" /></div>
    </section>
  </div>
</template>

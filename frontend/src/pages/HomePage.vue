<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { api, fmtDate } from '../lib/api'

const filter = ref('')
const items = ref([])
const error = ref('')
let timer

async function load() {
  try {
    items.value = await api(`/broadcasts${filter.value ? `?status=${filter.value}` : ''}`)
    error.value = ''
  } catch (e) {
    error.value = e.message
  }
}

watch(filter, load)
onMounted(() => {
  load()
  timer = setInterval(load, 10000)
})
onUnmounted(() => clearInterval(timer))

const label = { LIVE: 'LIVE', READY: '방송 전', ENDED: '종료' }
</script>

<template>
  <div class="tabs">
    <button :class="{ on: filter === '' }" @click="filter = ''">전체</button>
    <button :class="{ on: filter === 'LIVE' }" @click="filter = 'LIVE'">방송 중</button>
    <button :class="{ on: filter === 'ENDED' }" @click="filter = 'ENDED'">종료</button>
  </div>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-else-if="!items.length" class="muted">방송이 없습니다.</p>
  <div class="grid">
    <RouterLink v-for="b in items" :key="b.id" :to="`/broadcasts/${b.id}`">
      <div class="thumb">
        <span class="badge" :class="b.status">{{ label[b.status] }}</span>
        <span>{{ b.sourceType === 'CAMERA' ? '📱 모바일 카메라' : b.sourceType === 'SCREEN' ? '🖥️ PC 화면' : '' }}</span>
      </div>
      <h2 style="margin-top: 10px">{{ b.title }}</h2>
      <div class="muted">{{ b.ownerNickname }} · {{ b.category || '일반' }}</div>
      <div class="muted">{{ b.status === 'LIVE' ? `시작 ${fmtDate(b.startedAt)}` : `등록 ${fmtDate(b.createdAt)}` }}</div>
    </RouterLink>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, clearAuth } from './lib/auth'
import { api } from './lib/api'
import { live, startLivePolling } from './lib/live'
import { avatarStyle, initial, fmtCount } from './lib/ui'

const route = useRoute()
const router = useRouter()
const q = ref(route.query.q || '')
const sideOpen = ref(false)

watch(() => route.fullPath, () => (sideOpen.value = false))
watch(() => route.query.q, (v) => (q.value = v || ''))
onMounted(startLivePolling)

function search() {
  router.push({ path: '/', query: q.value.trim() ? { q: q.value.trim() } : {} })
}

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } catch {}
  clearAuth()
  router.push('/')
}
</script>

<template>
  <header class="app-header">
    <button class="icon-btn ghost menu-toggle" aria-label="메뉴" @click="sideOpen = !sideOpen">☰</button>
    <RouterLink to="/" class="logo">OnAir <span class="onair">LIVE</span></RouterLink>
    <form class="search" role="search" @submit.prevent="search">
      <input v-model="q" placeholder="방송 제목, 방송자, 카테고리 검색" aria-label="검색" />
    </form>
    <div class="header-right">
      <template v-if="auth.token">
        <RouterLink to="/broadcasts/new" class="btn primary">● 방송하기</RouterLink>
        <span class="nick muted">{{ auth.user?.nickname }}</span>
        <div class="avatar" :style="avatarStyle(auth.user?.id)" :title="auth.user?.nickname">{{ initial(auth.user?.nickname) }}</div>
        <button class="ghost" @click="logout">로그아웃</button>
      </template>
      <template v-else>
        <RouterLink to="/login" class="btn primary">로그인</RouterLink>
      </template>
    </div>
  </header>

  <aside class="app-sidebar" :class="{ open: sideOpen }">
    <nav class="side-nav">
      <RouterLink to="/">🏠 홈</RouterLink>
      <RouterLink to="/recordings">🎞️ 다시보기</RouterLink>
      <RouterLink v-if="auth.token" to="/broadcasts/new">🎙️ 방송하기</RouterLink>
    </nav>
    <div class="side-title" style="margin-top: 14px">라이브 채널</div>
    <p v-if="live.loaded && !live.items.length" class="side-empty">지금 방송 중인 채널이 없어요.</p>
    <RouterLink v-for="b in live.items" :key="b.id" :to="`/broadcasts/${b.id}`" class="side-channel">
      <div class="avatar live" :style="avatarStyle(b.ownerId)">{{ initial(b.ownerNickname) }}</div>
      <div class="meta">
        <div class="name">{{ b.ownerNickname }}</div>
        <div class="sub">{{ b.category || '일반' }} · {{ b.title }}</div>
      </div>
      <div class="viewers"><span class="live-dot" />{{ fmtCount(b.viewerCount) }}</div>
    </RouterLink>
  </aside>

  <main class="app-main">
    <RouterView :key="$route.path" />
  </main>
</template>

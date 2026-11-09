<script setup>
import { useRouter } from 'vue-router'
import { auth, clearAuth } from './lib/auth'
import { api } from './lib/api'

const router = useRouter()

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } catch {}
  clearAuth()
  router.push('/')
}
</script>

<template>
  <header class="top">
    <div class="container">
      <RouterLink to="/" class="logo">On<span>Air</span></RouterLink>
      <nav>
        <RouterLink to="/">방송</RouterLink>
        <RouterLink to="/recordings">다시보기</RouterLink>
      </nav>
      <div class="spacer" />
      <template v-if="auth.token">
        <RouterLink to="/broadcasts/new" class="btn primary">방송 만들기</RouterLink>
        <span class="muted">{{ auth.user?.nickname }}</span>
        <button class="ghost" @click="logout">로그아웃</button>
      </template>
      <template v-else>
        <RouterLink to="/login" class="btn">로그인</RouterLink>
        <RouterLink to="/signup" class="btn ghost">회원가입</RouterLink>
      </template>
    </div>
  </header>
  <main class="container">
    <RouterView :key="$route.fullPath" />
  </main>
</template>

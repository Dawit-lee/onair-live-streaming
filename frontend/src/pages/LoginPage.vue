<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../lib/api'
import { setAuth } from '../lib/auth'

const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const demos = ref([])

onMounted(async () => {
  try {
    demos.value = await api('/auth/demo-accounts')
  } catch {}
})

function done(res) {
  setAuth(res)
  router.replace(route.query.next || '/')
}

async function login() {
  error.value = ''
  try {
    done(await api('/auth/login', { method: 'POST', body: { username: username.value, password: password.value } }))
  } catch (e) {
    error.value = e.message
  }
}

async function demoLogin(i) {
  try {
    done(await api(`/auth/demo-login/${i}`, { method: 'POST' }))
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="card narrow">
    <h1>로그인</h1>
    <form @submit.prevent="login">
      <label>아이디</label>
      <input v-model="username" autocomplete="username" required />
      <label>비밀번호</label>
      <input v-model="password" type="password" autocomplete="current-password" required />
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" style="width: 100%; margin-top: 16px">로그인</button>
    </form>
    <div v-if="demos.length" style="margin-top: 20px">
      <div class="muted" style="margin-bottom: 8px">평가용 테스트 계정으로 바로 로그인</div>
      <div class="row">
        <button v-for="(d, i) in demos" :key="d.username" @click="demoLogin(i)">{{ d.nickname }} ({{ d.username }})</button>
      </div>
    </div>
    <p class="muted" style="margin-top: 16px">계정이 없나요? <RouterLink to="/signup" style="color: var(--accent)">회원가입</RouterLink></p>
  </div>
</template>

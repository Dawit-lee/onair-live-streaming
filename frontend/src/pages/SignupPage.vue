<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../lib/api'
import { setAuth } from '../lib/auth'

const router = useRouter()
const form = ref({ username: '', password: '', nickname: '' })
const error = ref('')

async function signup() {
  error.value = ''
  try {
    setAuth(await api('/auth/signup', { method: 'POST', body: form.value }))
    router.replace('/')
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="card narrow">
    <h1>회원가입</h1>
    <form @submit.prevent="signup">
      <label>아이디 (영문/숫자/_ 4~20자)</label>
      <input v-model="form.username" autocomplete="username" required minlength="4" maxlength="20" pattern="[a-zA-Z0-9_]+" />
      <label>비밀번호 (8자 이상)</label>
      <input v-model="form.password" type="password" autocomplete="new-password" required minlength="8" />
      <label>닉네임</label>
      <input v-model="form.nickname" required maxlength="20" />
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" style="width: 100%; margin-top: 16px">가입하고 로그인</button>
    </form>
  </div>
</template>

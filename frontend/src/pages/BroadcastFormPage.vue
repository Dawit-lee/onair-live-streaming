<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../lib/api'

const route = useRoute()
const router = useRouter()
const id = route.params.id
const form = ref({ title: '', description: '', category: '' })
const error = ref('')

onMounted(async () => {
  if (!id) return
  try {
    const b = await api(`/broadcasts/${id}`)
    form.value = { title: b.title, description: b.description || '', category: b.category || '' }
  } catch (e) {
    error.value = e.message
  }
})

async function save() {
  error.value = ''
  try {
    const b = id
      ? await api(`/broadcasts/${id}`, { method: 'PATCH', body: form.value })
      : await api('/broadcasts', { method: 'POST', body: form.value })
    router.replace(id ? `/broadcasts/${b.id}` : `/studio/${b.id}`)
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="card narrow" style="max-width: 560px">
    <h1>{{ id ? '방송 정보 수정' : '방송 만들기' }}</h1>
    <form @submit.prevent="save">
      <label>제목</label>
      <input v-model="form.title" required maxlength="100" />
      <label>카테고리</label>
      <select v-model="form.category">
        <option value="">일반</option>
        <option>게임</option>
        <option>토크</option>
        <option>음악</option>
        <option>개발</option>
        <option>야외</option>
      </select>
      <label>설명</label>
      <textarea v-model="form.description" rows="4" maxlength="1000" />
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" style="width: 100%; margin-top: 16px">{{ id ? '저장' : '만들고 스튜디오로 이동' }}</button>
    </form>
  </div>
</template>

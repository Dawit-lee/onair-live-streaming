<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { api } from '../lib/api'
import { auth } from '../lib/auth'

const props = defineProps({ broadcastId: { type: [String, Number], required: true } })
const comments = ref([])
const text = ref('')
const error = ref('')
const editingId = ref(null)
const editText = ref('')
const listEl = ref(null)
let timer

async function load() {
  try {
    const next = await api(`/broadcasts/${props.broadcastId}/comments`)
    const atBottom = listEl.value && listEl.value.scrollHeight - listEl.value.scrollTop - listEl.value.clientHeight < 40
    const first = comments.value.length === 0
    const grew = next.length && next.at(-1)?.id !== comments.value.at(-1)?.id
    comments.value = next
    if (grew && (atBottom || first)) {
      await nextTick()
      listEl.value && (listEl.value.scrollTop = listEl.value.scrollHeight)
    }
  } catch {}
}

async function send() {
  if (!text.value.trim()) return
  error.value = ''
  try {
    await api(`/broadcasts/${props.broadcastId}/comments`, { method: 'POST', body: { content: text.value } })
    text.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function saveEdit(c) {
  try {
    await api(`/comments/${c.id}`, { method: 'PATCH', body: { content: editText.value } })
    editingId.value = null
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function remove(c) {
  if (!confirm('댓글을 삭제할까요?')) return
  try {
    await api(`/comments/${c.id}`, { method: 'DELETE' })
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="card chat">
    <h2>채팅 · 댓글</h2>
    <div ref="listEl" class="list">
      <p v-if="!comments.length" class="muted">첫 댓글을 남겨 보세요.</p>
      <div v-for="c in comments" :key="c.id" class="item">
        <template v-if="editingId === c.id">
          <form class="row" @submit.prevent="saveEdit(c)">
            <input v-model="editText" maxlength="500" style="flex: 1" />
            <button class="primary">저장</button>
            <button type="button" @click="editingId = null">취소</button>
          </form>
        </template>
        <template v-else>
          <b>{{ c.authorNickname }}</b>{{ c.content }}
          <span v-if="c.updatedAt !== c.createdAt" class="muted"> (수정됨)</span>
          <span v-if="auth.user?.id === c.authorId" class="actions">
            <button @click="editingId = c.id; editText = c.content">수정</button>
            <button @click="remove(c)">삭제</button>
          </span>
        </template>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <form v-if="auth.token" @submit.prevent="send">
      <input v-model="text" maxlength="500" placeholder="메시지 입력" />
      <button class="primary">전송</button>
    </form>
    <p v-else class="muted" style="margin-top: 10px">
      <RouterLink :to="`/login?next=/broadcasts/${broadcastId}`" style="color: var(--accent)">로그인</RouterLink> 후 댓글을 쓸 수 있습니다.
    </p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { api, fmtWon } from '../lib/api'
import { auth } from '../lib/auth'
import { nickColor } from '../lib/ui'
import DonationModal from './DonationModal.vue'
import { toast } from 'vue-sonner'
import { Gift, Coins, Mic } from '@lucide/vue'

const props = defineProps({ broadcast: { type: Object, required: true } })
const emit = defineEmits(['donation'])

const comments = ref([])
const donations = ref({ total: 0, count: 0, items: [] })
const config = ref({ enabled: false })
const text = ref('')
const editingId = ref(null)
const editText = ref('')
const listEl = ref(null)
const showDonate = ref(false)
let seenDonations = null
let timers = []

const isOwner = computed(() => auth.user?.id === props.broadcast.ownerId)
const donateBlocked = computed(() => {
  if (!config.value.enabled) return '결제 키가 설정되지 않아 후원이 비활성화되어 있습니다.'
  if (!auth.token) return '로그인 후 후원할 수 있습니다.'
  if (isOwner.value) return '내 방송에는 후원할 수 없습니다.'
  return ''
})

/** 댓글과 후원을 시간순으로 섞어 하나의 채팅 흐름으로 보여준다. */
const feed = computed(() =>
  [
    ...comments.value.map((c) => ({ kind: 'chat', key: `c${c.id}`, at: c.createdAt, ...c })),
    ...donations.value.items.map((d) => ({ kind: 'donation', key: `d${d.id}`, at: d.approvedAt, ...d })),
  ].sort((a, b) => new Date(a.at) - new Date(b.at)),
)

function nearBottom() {
  const el = listEl.value
  return !el || el.scrollHeight - el.scrollTop - el.clientHeight < 60
}
async function scrollDown(force) {
  if (!force && !nearBottom()) return
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

async function loadComments() {
  try {
    const stick = nearBottom()
    const next = await api(`/broadcasts/${props.broadcast.id}/comments`)
    const grew = next.at(-1)?.id !== comments.value.at(-1)?.id
    comments.value = next
    if (grew) scrollDown(stick)
  } catch {}
}

async function loadDonations() {
  try {
    const stick = nearBottom()
    const next = await api(`/broadcasts/${props.broadcast.id}/donations`)
    // 처음 불러온 이후 새로 들어온 후원만 플레이어 알림으로 띄운다.
    if (seenDonations) next.items.filter((d) => !seenDonations.has(d.id)).reverse().forEach((d) => emit('donation', d))
    seenDonations = new Set(next.items.map((d) => d.id))
    const grew = next.count !== donations.value.count
    donations.value = next
    if (grew) scrollDown(stick)
  } catch {}
}

async function send() {
  if (!text.value.trim()) return
  try {
    await api(`/broadcasts/${props.broadcast.id}/comments`, { method: 'POST', body: { content: text.value } })
    text.value = ''
    await loadComments()
    scrollDown(true)
  } catch (e) {
    toast.error(e.message)
  }
}

async function saveEdit(c) {
  try {
    await api(`/comments/${c.id}`, { method: 'PATCH', body: { content: editText.value } })
    editingId.value = null
    await loadComments()
  } catch (e) {
    toast.error(e.message)
  }
}

async function remove(c) {
  if (!confirm('메시지를 삭제할까요?')) return
  try {
    await api(`/comments/${c.id}`, { method: 'DELETE' })
    await loadComments()
  } catch (e) {
    toast.error(e.message)
  }
}

function openDonate() {
  if (donateBlocked.value) return toast.info(donateBlocked.value)
  showDonate.value = true
}

onMounted(async () => {
  await Promise.all([loadComments(), loadDonations()])
  scrollDown(true)
  timers = [setInterval(loadComments, 3000), setInterval(loadDonations, 4000)]
  try {
    config.value = await api('/donations/config')
  } catch {}
})
onUnmounted(() => timers.forEach(clearInterval))
</script>

<template>
  <section class="chat" aria-label="채팅">
    <div class="chat-head">
      <span>채팅</span>
      <span class="total" title="누적 후원"><Coins :size="13" /> {{ fmtWon(donations.total) }} · {{ donations.count }}건</span>
    </div>
    <div ref="listEl" class="chat-list">
      <div class="chat-system">채팅방에 오신 것을 환영합니다. 서로 배려하는 채팅 문화를 만들어 주세요.</div>
      <template v-for="m in feed" :key="m.key">
        <div v-if="m.kind === 'donation'" class="chat-donation">
          <div><b :style="{ color: nickColor(m.id) }">{{ m.donorNickname }}</b>님이 <span class="amt">{{ fmtWon(m.amount) }}</span> 후원했어요</div>
          <div v-if="m.message" class="msg">{{ m.message }}</div>
        </div>
        <div v-else class="chat-msg">
          <form v-if="editingId === m.id" class="row" @submit.prevent="saveEdit(m)">
            <input v-model="editText" maxlength="500" style="flex: 1; height: 32px; padding: 4px 8px" />
            <button class="primary" style="height: 32px">저장</button>
            <button type="button" style="height: 32px" @click="editingId = null">취소</button>
          </form>
          <template v-else>
            <span class="nick" :style="{ color: nickColor(m.authorId) }">{{ m.authorNickname }}<Mic v-if="m.authorId === broadcast.ownerId" :size="12" class="owner-mark" aria-label="방송자" /></span>{{ m.content }}<span v-if="m.updatedAt !== m.createdAt" class="edited">(수정됨)</span>
            <span v-if="auth.user?.id === m.authorId" class="actions">
              <button @click="editingId = m.id; editText = m.content">수정</button>
              <button @click="remove(m)">삭제</button>
            </span>
          </template>
        </div>
      </template>
    </div>
    <div class="chat-input">
      <form v-if="auth.token" @submit.prevent="send">
        <button type="button" class="ghost" :title="donateBlocked || '후원하기'" :disabled="!!donateBlocked && isOwner" @click="openDonate"><Gift :size="16" /> 후원</button>
        <input v-model="text" maxlength="500" placeholder="채팅을 입력해 주세요" aria-label="채팅 입력" />
        <button class="primary">전송</button>
      </form>
      <div v-else class="login-hint">
        <RouterLink :to="`/login?next=/broadcasts/${broadcast.id}`" style="color: var(--accent); font-weight: 700">로그인</RouterLink>하고 채팅과 후원에 참여하세요
      </div>
    </div>
    <DonationModal v-if="showDonate" :broadcast="broadcast" @close="showDonate = false" />
  </section>
</template>

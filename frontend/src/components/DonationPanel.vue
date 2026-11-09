<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api, fmtWon } from '../lib/api'
import { auth } from '../lib/auth'

const props = defineProps({ broadcast: { type: Object, required: true } })
const summary = ref({ total: 0, count: 0, items: [] })
const config = ref({ enabled: false })
const amount = ref(1000)
const message = ref('')
const error = ref('')
const busy = ref(false)
let timer

const presets = [1000, 5000, 10000, 50000]

async function load() {
  try {
    summary.value = await api(`/broadcasts/${props.broadcast.id}/donations`)
  } catch {}
}

function loadTossSdk() {
  if (window.TossPayments) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://js.tosspayments.com/v2/standard'
    s.onload = resolve
    s.onerror = () => reject(new Error('결제 모듈을 불러오지 못했습니다.'))
    document.head.appendChild(s)
  })
}

async function donate() {
  error.value = ''
  busy.value = true
  try {
    // 1) 서버에 주문(후원 대상/금액) 저장 → 2) 토스 테스트 결제창 → 3) 성공 페이지에서 서버 승인
    const order = await api(`/broadcasts/${props.broadcast.id}/donations/orders`, {
      method: 'POST',
      body: { amount: Number(amount.value), message: message.value },
    })
    await loadTossSdk()
    const payment = window.TossPayments(order.clientKey).payment({ customerKey: order.customerKey })
    const base = `${location.origin}/donation`
    await payment.requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: order.amount },
      orderId: order.orderId,
      orderName: order.orderName,
      successUrl: `${base}/success?broadcastId=${props.broadcast.id}`,
      failUrl: `${base}/fail?broadcastId=${props.broadcast.id}`,
    })
  } catch (e) {
    if (e.code !== 'USER_CANCEL') error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  load()
  timer = setInterval(load, 10000)
  try {
    config.value = await api('/donations/config')
  } catch {}
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="card">
    <h2>후원 · 누적 {{ fmtWon(summary.total) }} ({{ summary.count }}건)</h2>
    <template v-if="config.enabled && auth.token && auth.user?.id !== broadcast.ownerId">
      <div class="muted" style="margin-bottom: 6px">후원 대상: {{ broadcast.ownerNickname }}</div>
      <div class="amounts">
        <button v-for="p in presets" :key="p" :class="{ on: amount === p }" @click="amount = p">{{ fmtWon(p) }}</button>
      </div>
      <input v-model.number="amount" type="number" min="1000" max="1000000" step="1000" style="margin-top: 8px" />
      <input v-model="message" maxlength="200" placeholder="응원 메시지 (선택)" style="margin-top: 8px" />
      <button class="primary" style="width: 100%; margin-top: 8px" :disabled="busy" @click="donate">
        테스트 결제로 후원하기
      </button>
      <p class="muted" style="font-size: 12px">토스페이먼츠 테스트 모드입니다. 실제로 결제되지 않습니다.</p>
    </template>
    <p v-else-if="!config.enabled" class="muted">결제 키가 설정되지 않아 후원이 비활성화되어 있습니다.</p>
    <p v-else-if="!auth.token" class="muted">로그인하면 후원할 수 있습니다.</p>
    <p v-if="error" class="error">{{ error }}</p>
    <div v-for="d in summary.items.slice(0, 10)" :key="d.id" class="muted" style="margin-top: 6px">
      💚 <b style="color: var(--text)">{{ d.donorNickname }}</b> {{ fmtWon(d.amount) }} <span v-if="d.message">— {{ d.message }}</span>
    </div>
  </div>
</template>

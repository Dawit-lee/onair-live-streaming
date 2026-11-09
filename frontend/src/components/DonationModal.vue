<script setup>
import { ref } from 'vue'
import { api, fmtWon } from '../lib/api'
import { avatarStyle, initial } from '../lib/ui'

const props = defineProps({ broadcast: { type: Object, required: true }, clientKey: String })
const emit = defineEmits(['close'])
const presets = [1000, 5000, 10000, 50000]
const amount = ref(1000)
const message = ref('')
const error = ref('')
const busy = ref(false)

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
</script>

<template>
  <div class="modal-back" @click.self="emit('close')">
    <div class="modal" role="dialog" aria-label="후원하기">
      <div class="modal-head">
        <h2>💸 후원하기</h2>
        <button class="icon-btn ghost" aria-label="닫기" @click="emit('close')">✕</button>
      </div>
      <div class="row" style="margin-bottom: 14px">
        <div class="avatar" :style="avatarStyle(broadcast.ownerId)">{{ initial(broadcast.ownerNickname) }}</div>
        <div>
          <div style="font-weight: 800">{{ broadcast.ownerNickname }}</div>
          <div class="muted">후원 대상 방송자</div>
        </div>
      </div>
      <div class="amounts">
        <button v-for="p in presets" :key="p" :class="{ on: amount === p }" @click="amount = p">{{ p / 1000 }}천원</button>
      </div>
      <label>후원 금액 (1,000원 ~ 1,000,000원)</label>
      <input v-model.number="amount" type="number" min="1000" max="1000000" step="1000" />
      <label>후원 메시지 (선택, 채팅에 표시됩니다)</label>
      <input v-model="message" maxlength="200" placeholder="응원 메시지를 남겨 보세요" />
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary lg" style="width: 100%; margin-top: 16px" :disabled="busy" @click="donate">
        {{ fmtWon(amount) }} 후원하기
      </button>
      <p class="muted" style="font-size: 12px; text-align: center; margin: 10px 0 0">토스페이먼츠 테스트 모드 · 실제로 결제되지 않습니다</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api, fmtWon } from '../lib/api'
import { toast } from 'vue-sonner'

const route = useRoute()
const q = route.query
const ok = route.path.endsWith('success')
const state = ref(ok ? 'loading' : 'fail')
const message = ref(q.message || '')

onMounted(async () => {
  if (!ok) return
  try {
    // 결제창이 넘겨준 값은 그대로 믿지 않고 서버가 주문/토스 승인 결과와 대조한다.
    await api('/donations/confirm', {
      method: 'POST',
      body: { paymentKey: q.paymentKey, orderId: q.orderId, amount: Number(q.amount) },
    })
    state.value = 'done'
    toast.success(`${fmtWon(q.amount)} 후원 완료!`)
  } catch (e) {
    state.value = 'fail'
    message.value = e.message
    toast.error(`후원 실패: ${e.message}`)
  }
})
</script>

<template>
  <div class="card narrow" style="text-align: center">
    <template v-if="state === 'loading'"><h1>결제 승인 확인 중…</h1></template>
    <template v-else-if="state === 'done'">
      <h1>{{ fmtWon(q.amount) }} 후원 완료!</h1>
    </template>
    <template v-else>
      <h1>후원 실패</h1>
      <p class="error">{{ message }}</p>
    </template>
    <RouterLink v-if="q.broadcastId" :to="`/broadcasts/${q.broadcastId}`" class="btn primary" style="margin-top: 16px">
      방송으로 돌아가기
    </RouterLink>
  </div>
</template>

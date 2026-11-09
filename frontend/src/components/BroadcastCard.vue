<script setup>
import { ref } from 'vue'
import { avatarStyle, initial, fmtCount, timeAgo, thumbUrl } from '../lib/ui'

const props = defineProps({ b: { type: Object, required: true } })
const noThumb = ref(false)
</script>

<template>
  <RouterLink :to="`/broadcasts/${b.id}`" class="bcard" :class="{ ended: b.status !== 'LIVE' }">
    <div class="thumb">
      <img v-if="!noThumb" :src="thumbUrl(b.id)" alt="" @error="noThumb = true" />
      <div v-if="noThumb" class="ph" :style="avatarStyle(b.ownerId)">{{ initial(b.ownerNickname) }}</div>
      <div class="tl">
        <span v-if="b.status === 'LIVE'" class="badge-live">LIVE</span>
        <span v-else class="badge">{{ b.status === 'READY' ? '방송 예정' : '방송 종료' }}</span>
        <span v-if="b.sourceType" class="badge">{{ b.sourceType === 'CAMERA' ? '📱 모바일' : '🖥️ PC' }}</span>
      </div>
      <div v-if="b.status === 'LIVE'" class="bl"><span class="badge">{{ fmtCount(b.viewerCount) }}명 시청</span></div>
      <div v-else-if="b.endedAt" class="br"><span class="badge">{{ timeAgo(b.endedAt) }}</span></div>
    </div>
    <div class="info">
      <div class="avatar" :class="{ live: b.status === 'LIVE' }" :style="avatarStyle(b.ownerId)">{{ initial(b.ownerNickname) }}</div>
      <div style="min-width: 0">
        <div class="title">{{ b.title }}</div>
        <div class="owner">{{ b.ownerNickname }}</div>
        <div class="tags"><span class="tag">{{ b.category || '일반' }}</span></div>
      </div>
    </div>
  </RouterLink>
</template>

import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './lib/auth'

const routes = [
  { path: '/', component: () => import('./pages/HomePage.vue') },
  { path: '/login', component: () => import('./pages/LoginPage.vue') },
  { path: '/signup', component: () => import('./pages/SignupPage.vue') },
  { path: '/broadcasts/new', component: () => import('./pages/BroadcastFormPage.vue'), meta: { auth: true } },
  { path: '/broadcasts/:id', component: () => import('./pages/WatchPage.vue') },
  { path: '/broadcasts/:id/edit', component: () => import('./pages/BroadcastFormPage.vue'), meta: { auth: true } },
  { path: '/studio/:id', component: () => import('./pages/StudioPage.vue'), meta: { auth: true } },
  { path: '/recordings', component: () => import('./pages/RecordingsPage.vue') },
  { path: '/recordings/:id', component: () => import('./pages/RecordingPage.vue') },
  { path: '/donation/success', component: () => import('./pages/DonationResultPage.vue') },
  { path: '/donation/fail', component: () => import('./pages/DonationResultPage.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({ history: createWebHistory(), routes })

// 화면 가드는 편의용이다. 실제 권한 검증은 서버가 한다.
router.beforeEach((to) => {
  if (to.meta.auth && !auth.token) return { path: '/login', query: { next: to.fullPath } }
})

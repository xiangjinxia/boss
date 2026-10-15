import { createRouter, createWebHistory } from 'vue-router'
import { session } from './services/api'
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: () => import('./pages/Market.vue') },
    { path: '/goods/:id', component: () => import('./pages/Detail.vue') },
    { path: '/login', component: () => import('./pages/Auth.vue') },
    { path: '/register', component: () => import('./pages/Auth.vue') },
    { path: '/publish', component: () => import('./pages/Publish.vue'), meta: { auth: true } },
    { path: '/edit/:id', component: () => import('./pages/Publish.vue'), meta: { auth: true } },
    { path: '/me', component: () => import('./pages/Personal.vue'), meta: { auth: true } },
    { path: '/messages', component: () => import('./pages/Messages.vue'), meta: { auth: true } },
    {
      path: '/admin',
      component: () => import('./pages/Admin.vue'),
      meta: { admin: true, auth: true }
    },
    { path: '/:pathMatch(.*)*', component: () => import('./pages/NotFound.vue') }
  ]
})
router.beforeEach((to) => {
  if (to.meta.auth && !session.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.admin && session.user?.role !== 'ADMIN') return '/'
})
export default router

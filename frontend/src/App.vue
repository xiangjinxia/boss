<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { session, http, clearSession, task } from './services/api'
import Icon from './components/Icon.vue'
const router = useRouter(),
  menu = ref(false),
  unread = ref(0)
const route = useRoute()
let timer
async function refreshUnread() {
  if (!session.user) {
    unread.value = 0
    return
  }
  try {
    const data = await http.get('/me/notifications', { params: { size: 1 }, quiet: true })
    unread.value = data.unread
  } catch {}
}
watch(() => [session.user?.id, route.fullPath], refreshUnread)
onMounted(() => {
  refreshUnread()
  timer = setInterval(refreshUnread, 30000)
})
onUnmounted(() => clearInterval(timer))
async function logout() {
  await task(() => http.post('/auth/logout'))
  clearSession()
  router.push('/')
}
</script>
<template>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink to="/" class="brand" @click="menu = false"
        ><span class="brand-icon"><Icon name="leaf" :size="25" /></span
        ><span class="brand-name">拾光<small>CAMPUS EXCHANGE</small></span></RouterLink
      >
      <nav class="desktop-nav">
        <RouterLink to="/" exact-active-class="active">校园集市</RouterLink
        ><RouterLink to="/messages">留言消息</RouterLink><RouterLink to="/me">我的校园</RouterLink
        ><RouterLink v-if="session.user?.role === 'ADMIN'" to="/admin">管理后台</RouterLink>
      </nav>
      <div class="header-actions">
        <RouterLink to="/publish" class="button primary publish-button"
          ><Icon name="plus" :size="18" />发布闲置</RouterLink
        ><template v-if="session.user"
          ><RouterLink to="/notifications" class="notification-bell" aria-label="站内通知"
            ><Icon name="bell" :size="20" /><span v-if="unread">{{
              unread > 99 ? '99+' : unread
            }}</span></RouterLink
          ><RouterLink to="/me" class="header-user" :title="session.user.nickname"
            ><img v-if="session.user.avatar" :src="session.user.avatar" alt="头像" /><span v-else>{{
              session.user.nickname.slice(0, 1)
            }}</span
            ><b>{{ session.user.nickname }}</b></RouterLink
          ><button class="icon-button logout-button" @click="logout" aria-label="退出登录">
            <Icon name="logout" :size="18" /></button></template
        ><RouterLink v-else to="/login" class="login-link">登录 / 注册</RouterLink
        ><button class="icon-button mobile-menu" @click="menu = !menu" aria-label="导航菜单">
          <Icon name="menu" />
        </button>
      </div>
    </div>
    <nav v-if="menu" class="mobile-nav" @click="menu = false">
      <RouterLink to="/">校园集市</RouterLink><RouterLink to="/messages">留言消息</RouterLink
      ><RouterLink to="/me">个人中心</RouterLink
      ><RouterLink v-if="session.user?.role === 'ADMIN'" to="/admin">管理后台</RouterLink>
    </nav>
  </header>
  <main><RouterView :key="$route.path" /></main>
  <footer class="site-footer">
    <div class="footer-inner">
      <RouterLink to="/" class="footer-logo"
        ><Icon name="leaf" :size="20" />拾光 <span>让每一份闲置，再次闪光。</span></RouterLink
      >
      <div>
        校园二手交易平台 <span class="footer-dot">·</span> 线下见面，安心交换
        <span class="footer-dot">·</span> © {{ new Date().getFullYear() }} Campus Exchange
      </div>
    </div>
  </footer>
</template>

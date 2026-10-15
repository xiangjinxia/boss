<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { http, date } from '../services/api'
import Icon from '../components/Icon.vue'
const router = useRouter(),
  data = ref({ items: [], total: 0, unread: 0 }),
  page = ref(1),
  unread = ref(false),
  loading = ref(false),
  failed = ref(false)
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true
  failed.value = false
  try {
    data.value = await http.get('/me/notifications', {
      params: { page: page.value, size: 20, unread: unread.value }
    })
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
async function open(n) {
  try {
    await http.post(`/me/notifications/${n.id}/read`)
    router.push(n.link)
  } catch {}
}
async function readAll() {
  try {
    await http.post('/me/notifications/read-all')
    await load(true)
  } catch {}
}
onMounted(() => load())
</script>
<template>
  <div class="container page-container notification-page">
    <div class="page-header">
      <div>
        <span class="eyebrow subtle">STAY IN THE LOOP</span>
        <h1 class="page-title">每个进展，都有回应。</h1>
        <p class="page-subtitle">审核、留言、订单和评价通知 · {{ data.unread }} 条未读</p>
      </div>
      <div class="row-actions">
        <button class="button ghost small" @click="load()">刷新</button
        ><button class="button primary small" :disabled="!data.unread" @click="readAll">
          全部标为已读
        </button>
      </div>
    </div>
    <section class="panel">
      <div class="panel-heading">
        <h2>站内通知</h2>
        <el-switch v-model="unread" active-text="只看未读" @change="load(true)" />
      </div>
      <el-skeleton v-if="loading" animated :rows="6" />
      <div v-else-if="failed" class="empty-state">
        <h3>暂时未能加载通知</h3>
        <button class="button primary" @click="load()">重试</button>
      </div>
      <div v-else-if="!data.items.length" class="empty-state">
        <Icon name="bell" :size="38" />
        <h3>{{ unread ? '未读通知已看完' : '暂时没有通知' }}</h3>
        <p>商品和交易有新进展时，会在这里留下记录。</p>
      </div>
      <button
        v-for="n in loading ? [] : data.items"
        :key="n.id"
        class="notification-item"
        :class="{ unread: !n.is_read }"
        @click="open(n)"
      >
        <span class="notification-icon"
          ><Icon :name="n.kind === 'ORDER' ? 'receipt' : n.kind === 'MESSAGE' ? 'message' : 'bell'"
        /></span>
        <div>
          <header>
            <b>{{ n.title }}</b
            ><span v-if="!n.is_read" class="unread-dot"></span><time>{{ date(n.created_at) }}</time>
          </header>
          <p>{{ n.content }}</p>
        </div>
        <Icon name="chevron" :size="17" /></button
      ><el-pagination
        v-if="data.total > 20"
        class="pagination"
        background
        layout="prev,pager,next"
        :total="data.total"
        :page-size="20"
        v-model:current-page="page"
        @current-change="load()"
      />
    </section>
  </div>
</template>

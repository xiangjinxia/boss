<script setup>
import { ref, onMounted } from 'vue'
import { http, date } from '../services/api'
import Icon from '../components/Icon.vue'
const items = ref([]),
  loading = ref(true),
  failed = ref(false)
async function load() {
  loading.value = true
  failed.value = false
  try {
    items.value = await http.get('/me/messages')
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="container page-container" style="max-width: 900px">
    <div class="page-header">
      <div>
        <span class="eyebrow subtle">STORIES START WITH A HELLO</span>
        <h1 class="page-title">留言消息</h1>
        <p class="page-subtitle">你发出的咨询、收到的回复，以及同学们对你好物的留言。</p>
      </div>
      <button class="button ghost small" @click="load" :disabled="loading">
        <Icon name="refresh" :size="15" />刷新
      </button>
    </div>
    <div v-if="loading" class="panel"><el-skeleton animated :rows="8" /></div>
    <div v-else-if="failed" class="empty-state">
      <h3>消息加载失败</h3>
      <button class="button primary" @click="load">重试</button>
    </div>
    <div v-else-if="!items.length" class="empty-state">
      <Icon name="message" :size="42" />
      <h3>还没有新的对话</h3>
      <p>看到喜欢的好物，就和同学打个招呼吧。</p>
      <RouterLink to="/" class="button primary">去逛逛</RouterLink>
    </div>
    <div v-else class="inbox-list">
      <RouterLink class="inbox-item" v-for="m in items" :key="m.id" :to="`/goods/${m.goods_id}`"
        ><header>
          <span class="mini-avatar">{{ m.sender_name.slice(0, 1) }}</span
          ><b>{{ m.sender_name }}</b
          ><span class="badge" v-if="m.reply_to_id">回复留言</span
          ><time>{{ date(m.created_at) }}</time>
        </header>
        <div v-if="m.reply_content" class="reply-quote">{{ m.reply_content }}</div>
        <p>{{ m.content }}</p>
        <footer>
          <Icon name="box" :size="12" />{{ m.goods_title
          }}<span style="margin-left: auto">查看对话</span><Icon name="right" :size="12" /></footer
      ></RouterLink>
    </div>
    <p
      v-if="items.length"
      class="muted"
      style="text-align: center; font-size: 10px; margin-top: 25px"
    >
      显示最近 200 条相关留言，点击商品进入留言区查看。
    </p>
  </div>
</template>

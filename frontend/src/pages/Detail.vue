<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, session, money, date, task } from '../services/api'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
const route = useRoute(),
  router = useRouter(),
  g = ref(null),
  comments = ref([]),
  loading = ref(true),
  failed = ref(false),
  active = ref(0),
  content = ref(''),
  reply = ref(null),
  busy = ref(false),
  reportOpen = ref(false),
  reportReason = ref('')
async function load() {
  loading.value = true
  failed.value = false
  try {
    ;[g.value, comments.value] = await Promise.all([
      http.get(`/goods/${route.params.id}`),
      http.get(`/goods/${route.params.id}/messages`)
    ])
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
function login() {
  if (!session.user) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}
async function favorite() {
  if (!login()) return
  await task(async () => {
    if (g.value.is_favorite) await http.delete(`/goods/${g.value.id}/favorite`)
    else await http.put(`/goods/${g.value.id}/favorite`)
    g.value.is_favorite = !g.value.is_favorite
    g.value.favorite_count += g.value.is_favorite ? 1 : -1
  })
}
async function buy() {
  if (!login()) return
  try {
    await ElMessageBox.confirm(
      '下单后等待卖家确认，再通过留言约定校内见面地点。线下交付完成后，由你确认完成。',
      '发起线下交易',
      { confirmButtonText: '确认下单', cancelButtonText: '再看看' }
    )
    busy.value = true
    await http.post('/orders', { goodsId: g.value.id })
    ElMessage.success('已下单，等待卖家确认')
    router.push('/me?tab=buying')
  } catch {
  } finally {
    busy.value = false
  }
}
async function send() {
  if (!login() || !content.value.trim()) return
  busy.value = true
  await task(async () => {
    await http.post(`/goods/${g.value.id}/messages`, {
      content: content.value,
      replyToId: reply.value?.id
    })
    content.value = ''
    reply.value = null
    comments.value = await http.get(`/goods/${g.value.id}/messages`)
    ElMessage.success('留言已发送')
  })
  busy.value = false
}
async function report() {
  if (!reportReason.value.trim()) return
  busy.value = true
  await task(async () => {
    await http.post('/reports', { goodsId: g.value.id, reason: reportReason.value })
    reportOpen.value = false
    reportReason.value = ''
    ElMessage.success('举报已提交，可以在个人中心查看处理结果')
  })
  busy.value = false
}
function startReport() {
  if (login()) reportOpen.value = true
}
onMounted(load)
</script>
<template>
  <div class="container page-container">
    <div class="breadcrumb">
      <RouterLink to="/">校园集市</RouterLink><Icon name="chevron" :size="13" /><span
        >商品详情</span
      >
    </div>
    <div v-if="loading" class="panel loading-panel"><el-skeleton animated :rows="10" /></div>
    <div v-else-if="failed || !g" class="empty-state">
      <Icon name="box" :size="40" />
      <h3>这件好物暂时不可查看</h3>
      <p>商品可能还在审核，或已经下架。</p>
      <RouterLink to="/" class="button primary">返回校园集市</RouterLink>
    </div>
    <template v-else
      ><section class="detail-layout">
        <div class="detail-gallery">
          <div class="main-image"><img :src="g.images[active]" :alt="g.title" /></div>
          <div v-if="g.images.length > 1" class="thumbnails">
            <button
              v-for="(img, i) in g.images"
              :key="i"
              :class="{ selected: active === i }"
              @click="active = i"
            >
              <img :src="img" :alt="`商品图片 ${i + 1}`" />
            </button>
          </div>
        </div>
        <div class="detail-info">
          <div class="detail-topline">
            <span>{{ g.category_name }} / 校园闲置</span
            ><button class="text-button" @click="startReport">
              <Icon name="flag" :size="13" />举报
            </button>
          </div>
          <h1>{{ g.title }}</h1>
          <StatusBadge :status="g.status" />
          <div class="detail-price">
            <div class="price"><small>¥</small>{{ money(g.price) }}</div>
            <span>线下交易 · 无需在线支付</span>
          </div>
          <div class="detail-attributes">
            <span><Icon name="sparkles" :size="15" />{{ g.condition_level }}</span
            ><span><Icon name="pin" :size="15" />校园内当面交易</span>
          </div>
          <RouterLink :to="`/sellers/${g.seller_id}`" class="seller-card">
            <img
              v-if="g.seller_avatar"
              :src="g.seller_avatar"
              class="mini-avatar"
              alt="卖家头像"
            /><span v-else class="mini-avatar">{{ g.seller_name.slice(0, 1) }}</span>
            <div>
              <b>{{ g.seller_name }}</b
              ><small>发布于 {{ date(g.created_at) }}</small>
            </div>
            <span class="badge active">查看小铺 <Icon name="right" :size="12" /></span>
          </RouterLink>
          <div class="detail-buttons">
            <RouterLink
              v-if="session.user?.id === g.seller_id && !['RESERVED', 'SOLD'].includes(g.status)"
              :to="`/edit/${g.id}`"
              class="button primary"
              >编辑我的商品</RouterLink
            ><button
              v-else
              class="button primary"
              :disabled="g.status !== 'LISTED' || session.user?.id === g.seller_id || busy"
              @click="buy"
            >
              <Icon name="bag" :size="18" />{{
                g.status === 'LISTED'
                  ? '我想要 · 发起交易'
                  : g.status === 'RESERVED'
                    ? '这件好物已被预订'
                    : '当前不可交易'
              }}</button
            ><button
              class="button ghost"
              :class="{ 'is-favorite': g.is_favorite }"
              @click="favorite"
            >
              <Icon name="heart" :size="18" />{{ g.is_favorite ? '已收藏' : '收藏' }}
            </button>
          </div>
          <div class="safety-note">
            <Icon name="shield" :size="15" /><span
              >请在校内公共场所见面，检查实物后再确认完成。不要向陌生人提前转账。</span
            >
          </div>
          <div v-if="g.reject_reason && session.user?.id === g.seller_id" class="reason-line">
            审核说明：{{ g.reject_reason }}
          </div>
        </div>
      </section>
      <section class="detail-lower">
        <div class="panel">
          <h2 class="panel-title">好物的故事<span class="muted">商品描述</span></h2>
          <p class="description">{{ g.description }}</p>
          <div style="height: 32px"></div>
          <h2 class="panel-title">
            留言聊一聊<span class="muted">{{ comments.length }} 条留言</span>
          </h2>
          <div v-if="reply" class="reply-quote">
            回复 {{ reply.sender_name }}：{{ reply.content }}
            <button class="text-button" @click="reply = null">取消</button>
          </div>
          <form class="comment-form" @submit.prevent="send">
            <el-input
              v-model="content"
              type="textarea"
              :rows="2"
              maxlength="500"
              :placeholder="session.user ? '想了解什么？问问同学吧…' : '登录后就可以留言咨询啦'"
              :disabled="!['LISTED', 'RESERVED'].includes(g.status)"
            /><button
              class="button primary small"
              :disabled="busy || !['LISTED', 'RESERVED'].includes(g.status)"
              type="submit"
            >
              {{ session.user ? '发送留言' : '登录留言' }}
            </button>
          </form>
          <div v-if="!comments.length" class="muted" style="padding: 14px 0">
            还没有留言，来开启第一句对话吧。
          </div>
          <div class="comment-list">
            <article class="comment" v-for="m in comments" :key="m.id">
              <span class="comment-avatar">{{ m.sender_name.slice(0, 1) }}</span>
              <div class="comment-body">
                <header>
                  <b>{{ m.sender_name }}</b
                  ><span v-if="m.sender_id === g.seller_id" class="badge listed">卖家</span
                  ><time>{{ date(m.created_at) }}</time>
                </header>
                <div v-if="m.reply_to_id" class="reply-quote">
                  回复 {{ m.reply_name }}：{{ m.reply_content }}
                </div>
                <p>{{ m.content }}</p>
                <button
                  v-if="
                    session.user?.id === g.seller_id &&
                    m.sender_id !== g.seller_id &&
                    ['LISTED', 'RESERVED'].includes(g.status)
                  "
                  class="text-button"
                  @click="reply = m"
                >
                  回复同学<Icon name="right" :size="12" />
                </button>
              </div>
            </article>
          </div>
        </div>
        <aside class="panel">
          <h3 class="panel-title"><Icon name="leaf" />让好物继续被喜欢</h3>
          <p class="description">
            这是一次校园里的小小交换。<br />先留言了解细节，再发起订单。<br />卖家确认后，约个方便的时间见面。<br />检查好物，确认完成，故事继续。
          </p>
        </aside>
      </section></template
    ><el-dialog v-model="reportOpen" title="举报商品" width="450px"
      ><p class="muted" style="margin-bottom: 16px">请描述商品的问题，管理员会记录并处理。</p>
      <el-input
        v-model="reportReason"
        type="textarea"
        :rows="4"
        maxlength="500"
        placeholder="例如：涉嫌违规、信息不真实…"
      /><template #footer
        ><button class="button ghost" @click="reportOpen = false">取消</button>
        <button class="button primary" :disabled="!reportReason.trim() || busy" @click="report">
          提交举报
        </button></template
      ></el-dialog
    >
  </div>
</template>

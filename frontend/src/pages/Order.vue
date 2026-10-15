<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, session, money, date } from '../services/api'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
const route = useRoute(),
  order = ref(null),
  loading = ref(true),
  failed = ref(false),
  busy = ref(false),
  rating = ref(5),
  content = ref('')
const buyer = computed(() => order.value?.buyer_id === session.user?.id),
  seller = computed(() => order.value?.seller_id === session.user?.id)
const steps = ['发起预约', '卖家接受', '线下交接', '买家确认完成']
const stage = computed(() =>
  order.value?.status === 'COMPLETED' ? 4 : order.value?.status === 'CONFIRMED' ? 2 : 1
)
async function load() {
  loading.value = true
  failed.value = false
  try {
    order.value = await http.get(`/orders/${route.params.id}`)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
const names = { confirm: '接受交易', reject: '拒绝订单', cancel: '取消交易', complete: '确认完成' }
async function action(a) {
  try {
    let reason = ''
    if (['cancel', 'reject'].includes(a)) {
      const r = await ElMessageBox.prompt(
        '可填写原因，帮助对方了解情况（最多 500 字）。',
        names[a],
        {
          inputType: 'textarea',
          inputValidator: (v) => !v || v.length <= 500 || '最多 500 字',
          confirmButtonText: names[a],
          cancelButtonText: '返回'
        }
      )
      reason = r.value || ''
    } else
      await ElMessageBox.confirm(
        a === 'complete'
          ? '确认已经当面收到并检查商品？完成后商品标为已售出，你可以留下评价。'
          : '接受后会为该买家预留商品，其他待确认预约自动取消。',
        names[a],
        { confirmButtonText: names[a], cancelButtonText: '再想想' }
      )
    busy.value = true
    await http.post(`/orders/${order.value.id}/actions`, { action: a, reason })
    ElMessage.success('订单已更新')
    await load()
  } catch {
  } finally {
    busy.value = false
  }
}
async function review() {
  if (content.value.trim().length < 5) {
    ElMessage.warning('请填写至少 5 个字的真实评价')
    return
  }
  busy.value = true
  try {
    await http.post(`/orders/${order.value.id}/review`, {
      rating: rating.value,
      content: content.value
    })
    ElMessage.success('评价已发布')
    await load()
  } catch {
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="container page-container order-page">
    <div class="breadcrumb">
      <RouterLink to="/me?tab=buying">我的交易</RouterLink
      ><Icon name="chevron" :size="13" />订单详情
    </div>
    <div v-if="loading" class="panel"><el-skeleton animated :rows="10" /></div>
    <div v-else-if="failed || !order" class="empty-state">
      <h2>订单暂时无法查看</h2>
      <p>请确认登录的是交易本人账号。</p>
      <button class="button primary" @click="load">重试</button>
    </div>
    <template v-else>
      <section class="panel order-overview">
        <div class="panel-heading">
          <div>
            <div class="eyebrow subtle">A SECOND CHANCE, A NEW STORY</div>
            <h1>每一步交易，都清清楚楚。</h1>
            <p class="muted">
              订单 #{{ String(order.id).padStart(6, '0') }} · {{ date(order.created_at) }}
            </p>
          </div>
          <StatusBadge
            :status="order.status"
            :text="order.status === 'PENDING' ? '待卖家确认' : undefined"
          />
        </div>
        <div v-if="order.status !== 'CANCELLED'" class="order-steps">
          <div v-for="(s, i) in steps" :key="s" :class="{ done: stage > i, current: i === stage }">
            <span>{{ stage > i ? '✓' : i + 1 }}</span
            ><b>{{ s }}</b
            ><small>{{
              i === 2 && order.status === 'CONFIRMED' ? '请通过商品留言约定时间地点' : ''
            }}</small>
          </div>
        </div>
        <div v-else class="hint-box">
          订单已取消：{{ order.cancel_reason }}。此订单不会继续成交。
        </div>
        <div class="order-product">
          <img :src="order.goods_image" :alt="order.goods_title" />
          <div>
            <RouterLink :to="`/goods/${order.goods_id}`"
              ><h2>{{ order.goods_title }}</h2></RouterLink
            >
            <p class="price"><small>¥</small>{{ money(order.amount) }}</p>
            <p class="muted">下单时的商品快照 · 线下交付</p>
            <p>
              买家：{{ order.buyer_name }} · 卖家：<RouterLink
                :to="`/sellers/${order.seller_id}`"
                class="text-button"
                >{{ order.seller_name }}</RouterLink
              >
            </p>
          </div>
        </div>
        <div class="row-actions" :class="{ 'action-loading': busy }">
          <template v-if="seller && order.status === 'PENDING'"
            ><button class="button primary" @click="action('confirm')">接受交易</button
            ><button class="button ghost" @click="action('reject')">拒绝订单</button></template
          ><button
            v-if="buyer && order.status === 'CONFIRMED'"
            class="button primary"
            @click="action('complete')"
          >
            确认完成</button
          ><button
            v-if="(buyer || seller) && ['PENDING', 'CONFIRMED'].includes(order.status)"
            class="button ghost"
            @click="action('cancel')"
          >
            取消交易</button
          ><RouterLink :to="`/goods/${order.goods_id}`" class="button ghost"
            >查看商品 / 留言</RouterLink
          ><RouterLink :to="`/sellers/${order.seller_id}`" class="button ghost"
            >去卖家小铺</RouterLink
          >
        </div>
      </section>
      <div class="order-detail-grid">
        <section class="panel">
          <h2 class="panel-title">交易进度</h2>
          <ol class="event-timeline">
            <li v-for="e in order.events" :key="e.id">
              <span class="event-dot"></span><time>{{ date(e.created_at) }}</time>
              <p>{{ e.detail }}</p>
              <small>{{ e.actor_name || '系统记录' }}</small>
            </li>
          </ol>
          <p class="chart-note">历史订单仅保留已知时间；升级前未记录的过程会明确标注。</p>
        </section>
        <section class="panel">
          <template v-if="order.review.length"
            ><h2 class="panel-title">这次交易的评价</h2>
            <el-rate :model-value="order.review[0].rating" disabled />
            <p class="description">{{ order.review[0].content }}</p>
            <small class="muted">{{ date(order.review[0].created_at) }}</small></template
          >
          <form
            v-else-if="buyer && order.status === 'COMPLETED'"
            class="review-form"
            @submit.prevent="review"
          >
            <h2 class="panel-title">好物交接后，留下真实感受</h2>
            <label>交易评分<el-rate v-model="rating" :allow-half="false" /></label
            ><el-input
              v-model="content"
              type="textarea"
              :rows="4"
              minlength="5"
              maxlength="500"
              show-word-limit
              placeholder="商品是否符合描述？交易体验如何？（5–500 字）"
            /><button class="button primary" type="submit" :disabled="busy">发布交易评价</button>
          </form>
          <template v-else
            ><h2 class="panel-title">校园交易小提醒</h2>
            <p class="description">
              先确认成色和配件，再约定校内公共场所见面。收到并检查商品后，由买家确认完成。
            </p>
            <p class="chart-note">
              完成订单后，买家可提交一次评价；卖家与管理员不能代替买家评价。
            </p></template
          >
        </section>
      </div>
    </template>
  </div>
</template>

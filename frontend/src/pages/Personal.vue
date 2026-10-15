<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, session, money, date, task, upload } from '../services/api'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
import GoodsCard from '../components/GoodsCard.vue'
const route = useRoute(),
  router = useRouter(),
  tab = ref('goods'),
  items = ref([]),
  summary = ref({}),
  loading = ref(false),
  failed = ref(false),
  busy = ref(false)
const tabs = [
  { id: 'goods', name: '我的发布', icon: 'box' },
  { id: 'buying', name: '我的购买', icon: 'bag' },
  { id: 'selling', name: '我的出售', icon: 'receipt' },
  { id: 'favorites', name: '我的收藏', icon: 'heart' },
  { id: 'reports', name: '我的举报', icon: 'flag' },
  { id: 'profile', name: '个人资料', icon: 'user' }
]
const profile = reactive({
  nickname: session.user.nickname,
  bio: session.user.bio,
  avatar: session.user.avatar
})
async function load() {
  tab.value = tabs.some((t) => t.id === route.query.tab) ? route.query.tab : 'goods'
  loading.value = true
  failed.value = false
  try {
    summary.value = await http.get('/me/summary')
    if (tab.value === 'profile') {
      Object.assign(profile, await http.get('/me'))
      items.value = []
    } else
      items.value = await http.get(
        ['buying', 'selling'].includes(tab.value) ? '/me/orders' : `/me/${tab.value}`,
        { params: { side: tab.value } }
      )
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
function select(t) {
  router.replace({ path: '/me', query: { tab: t } })
}
async function offline(g) {
  try {
    await ElMessageBox.confirm('下架后将不再出现在校园集市，待确认订单会自动取消。', '下架商品', {
      confirmButtonText: '确认下架',
      cancelButtonText: '取消'
    })
    await http.post(`/goods/${g.id}/offline`)
    ElMessage.success('商品已下架')
    await load()
  } catch {}
}
const actionText = {
  confirm: '接受交易',
  reject: '拒绝订单',
  cancel: '取消交易',
  complete: '确认完成'
}
async function action(o, a) {
  try {
    await ElMessageBox.confirm(
      a === 'complete'
        ? '请确认你已当面收到并检查商品。确认后交易完成，商品标记为已售出。'
        : a === 'confirm'
          ? '接受后商品将为这位买家预订，其他待确认订单会自动取消。'
          : `确定${actionText[a]}吗？`,
      actionText[a],
      { confirmButtonText: actionText[a], cancelButtonText: '再想想' }
    )
    busy.value = true
    await http.post(`/orders/${o.id}/actions`, { action: a })
    ElMessage.success('操作成功')
    await load()
  } catch {
  } finally {
    busy.value = false
  }
}
async function saveProfile() {
  if (!profile.nickname.trim()) {
    ElMessage.warning('请填写昵称')
    return
  }
  busy.value = true
  await task(async () => {
    session.user = await http.put('/me', profile)
    ElMessage.success('资料已更新')
  })
  busy.value = false
}
async function avatar(e) {
  const file = e.target.files[0]
  e.target.value = ''
  if (!file) return
  busy.value = true
  await task(async () => (profile.avatar = await upload(file)))
  busy.value = false
}
watch(() => route.query.tab, load)
onMounted(load)
</script>
<template>
  <div class="container page-container">
    <section class="personal-cover">
      <div class="profile-avatar">
        <img v-if="session.user?.avatar" :src="session.user.avatar" alt="头像" /><span v-else>{{
          session.user?.nickname.slice(0, 1)
        }}</span>
      </div>
      <div>
        <h1>你好，{{ session.user?.nickname }}</h1>
        <p>{{ session.user?.bio || '每一次交换，都是一段新的校园故事。' }}</p>
      </div>
      <div class="personal-stats">
        <div>
          <strong>{{ summary.published || 0 }}</strong
          ><small>我的发布</small>
        </div>
        <div>
          <strong>{{ summary.buying || 0 }}</strong
          ><small>购买记录</small>
        </div>
        <div>
          <strong>{{ summary.selling || 0 }}</strong
          ><small>出售记录</small>
        </div>
        <div>
          <strong>{{ summary.favorites || 0 }}</strong
          ><small>心动收藏</small>
        </div>
      </div>
    </section>
    <div class="workspace-layout">
      <nav class="side-nav">
        <button
          v-for="t in tabs"
          :key="t.id"
          :class="{ active: tab === t.id }"
          @click="select(t.id)"
        >
          <Icon :name="t.icon" :size="18" />{{ t.name }}
        </button>
      </nav>
      <section class="workspace-content">
        <div class="panel">
          <div class="panel-heading">
            <h2>{{ tabs.find((t) => t.id === tab)?.name }}</h2>
            <RouterLink v-if="tab === 'goods'" to="/publish" class="button primary small"
              ><Icon name="plus" :size="14" />发布闲置</RouterLink
            ><span v-else-if="tab !== 'profile'">共 {{ items.length }} 条记录</span>
          </div>
          <el-skeleton v-if="loading" animated :rows="8" />
          <div v-else-if="failed" class="empty-state">
            <h3>加载失败</h3>
            <button class="button primary" @click="load">重试</button>
          </div>
          <template v-else-if="tab === 'profile'"
            ><form class="profile-form" @submit.prevent="saveProfile">
              <div class="profile-avatar-upload">
                <div class="profile-avatar">
                  <img v-if="profile.avatar" :src="profile.avatar" alt="头像预览" /><span v-else>{{
                    profile.nickname.slice(0, 1)
                  }}</span>
                </div>
                <label class="upload-trigger"
                  ><input
                    type="file"
                    accept="image/jpeg,image/png"
                    :disabled="busy"
                    @change="avatar"
                  /><span>上传新头像</span
                  ><small class="form-help">JPG / PNG，单张 ≤ 5MB</small></label
                >
              </div>
              <label class="form-field"
                ><span>昵称</span
                ><el-input v-model="profile.nickname" maxlength="24" required /></label
              ><label class="form-field"
                ><span>个人签名</span
                ><el-input
                  v-model="profile.bio"
                  type="textarea"
                  :rows="3"
                  maxlength="200"
                  placeholder="简单介绍一下自己吧" /></label
              ><label class="form-field"
                ><span>账号</span><el-input :model-value="session.user.username" disabled /></label
              ><button class="button primary" type="submit" :disabled="busy">保存资料</button>
            </form></template
          >
          <div v-else-if="!items.length" class="empty-state">
            <Icon :name="tabs.find((t) => t.id === tab)?.icon" :size="38" />
            <h3>这里的故事，还等你开启</h3>
            <p>
              {{
                tab === 'goods'
                  ? '分享一件闲置，让好物继续被喜欢。'
                  : tab === 'reports'
                    ? '还没有举报记录，安心逛逛吧。'
                    : '去校园集市，发现下一份心动。'
              }}
            </p>
            <RouterLink :to="tab === 'goods' ? '/publish' : '/'" class="button primary">{{
              tab === 'goods' ? '发布第一件闲置' : '逛逛校园集市'
            }}</RouterLink>
          </div>
          <div v-else-if="tab === 'favorites'" class="goods-grid">
            <GoodsCard v-for="g in items" :key="g.id" :goods="g" status />
          </div>
          <div v-else-if="tab === 'goods'" class="my-goods-list">
            <article v-for="g in items" :key="g.id" class="list-row">
              <RouterLink :to="`/goods/${g.id}`"
                ><img class="row-image" :src="g.cover" :alt="g.title"
              /></RouterLink>
              <div class="row-info">
                <RouterLink :to="`/goods/${g.id}`"
                  ><h3>{{ g.title }}</h3></RouterLink
                >
                <div class="row-meta">
                  <span class="row-price">¥ {{ money(g.price) }}</span
                  ><span>{{ g.category_name }} · {{ date(g.created_at) }}</span>
                </div>
                <p v-if="g.reject_reason" class="reason-line">审核说明：{{ g.reject_reason }}</p>
                <div class="row-actions" v-if="!['SOLD', 'RESERVED'].includes(g.status)">
                  <RouterLink :to="`/edit/${g.id}`" class="button ghost small"
                    >编辑 / 重新提交</RouterLink
                  ><button
                    v-if="g.status !== 'OFFLINE'"
                    class="button ghost small"
                    @click="offline(g)"
                  >
                    下架
                  </button>
                </div>
              </div>
              <StatusBadge class="row-status" :status="g.status" />
            </article>
          </div>
          <div v-else-if="tab === 'reports'" class="my-goods-list">
            <article class="list-row" v-for="r in items" :key="r.id">
              <div class="row-info">
                <h3>{{ r.goods_title }}</h3>
                <p class="description">{{ r.reason }}</p>
                <div class="row-meta">提交于 {{ date(r.created_at) }}</div>
                <div v-if="r.result" class="reason-line">
                  处理结果：{{ r.result }} · {{ date(r.handled_at) }}
                </div>
              </div>
              <StatusBadge
                :status="r.status"
                :text="r.status === 'PENDING' ? '待处理' : undefined"
              />
            </article>
          </div>
          <div v-else class="my-goods-list">
            <article v-for="o in items" :key="o.id" class="list-row">
              <img class="row-image" :src="o.goods_image" :alt="o.goods_title" />
              <div class="row-info">
                <div class="order-number">
                  订单 #{{ String(o.id).padStart(6, '0') }} · {{ date(o.created_at) }}
                </div>
                <h3>{{ o.goods_title }}</h3>
                <div class="row-meta">
                  <span class="row-price">¥ {{ money(o.amount) }}</span
                  ><span
                    >{{ tab === 'buying' ? '卖家' : '买家' }}：{{
                      tab === 'buying' ? o.seller_name : o.buyer_name
                    }}</span
                  >
                </div>
                <div v-if="o.cancel_reason" class="reason-line">{{ o.cancel_reason }}</div>
                <div class="row-actions" :class="{ 'action-loading': busy }">
                  <template v-if="tab === 'selling' && o.status === 'PENDING'"
                    ><button class="button primary small" @click="action(o, 'confirm')">
                      接受交易</button
                    ><button class="button ghost small" @click="action(o, 'reject')">
                      拒绝订单
                    </button></template
                  ><button
                    v-if="tab === 'buying' && o.status === 'CONFIRMED'"
                    class="button primary small"
                    @click="action(o, 'complete')"
                  >
                    确认完成</button
                  ><button
                    v-if="['PENDING', 'CONFIRMED'].includes(o.status)"
                    class="button ghost small"
                    @click="action(o, 'cancel')"
                  >
                    取消交易</button
                  ><RouterLink :to="`/goods/${o.goods_id}`" class="button ghost small"
                    >查看商品 / 留言</RouterLink
                  >
                </div>
              </div>
              <StatusBadge
                class="row-status"
                :status="o.status"
                :text="o.status === 'PENDING' ? '待卖家确认' : undefined"
              />
            </article>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

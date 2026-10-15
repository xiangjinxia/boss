<script setup>
import { computed, ref, reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, money, date, task } from '../services/api'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
import Chart from '../components/Chart.vue'
import ConfigManager from '../components/ConfigManager.vue'
const route = useRoute(),
  router = useRouter(),
  tab = ref('dashboard'),
  rows = ref([]),
  dashboard = ref(null),
  loading = ref(false),
  failed = ref(false),
  busy = ref(false),
  currentPage = ref(1),
  total = ref(0),
  pageSize = ref(20)
const tabs = [
  { id: 'dashboard', name: '运营总览', icon: 'dashboard' },
  { id: 'goods', name: '商品审核', icon: 'audit' },
  { id: 'users', name: '用户管理', icon: 'users' },
  { id: 'reports', name: '举报管理', icon: 'flag' },
  { id: 'orders', name: '订单管理', icon: 'receipt' },
  { id: 'categories', name: '分类管理', icon: 'box' },
  { id: 'announcements', name: '公告管理', icon: 'bell' },
  { id: 'audit', name: '操作日志', icon: 'clock' }
]
const filters = reactive({ status: '', q: '', buyer: '', seller: '', from: '', to: '' })
function select(id) {
  currentPage.value = 1
  Object.assign(filters, {
    status: id === 'goods' ? 'PENDING' : id === 'reports' ? 'PENDING' : '',
    q: '',
    buyer: '',
    seller: '',
    from: '',
    to: ''
  })
  router.replace({ path: '/admin', query: { tab: id } })
}
async function load(reset = false) {
  if (reset === true) currentPage.value = 1
  tab.value = tabs.some((t) => t.id === route.query.tab) ? route.query.tab : 'dashboard'
  loading.value = true
  failed.value = false
  try {
    const data = await http.get(`/admin/${tab.value}`, {
      params: { ...filters, page: currentPage.value, size: pageSize.value }
    })
    if (tab.value === 'dashboard') {
      dashboard.value = data
      filters.from = data.from
      filters.to = data.to
    } else {
      rows.value = data.items
      total.value = data.total
      if (currentPage.value > Math.max(1, Math.ceil(total.value / pageSize.value))) {
        currentPage.value = Math.max(1, Math.ceil(total.value / pageSize.value))
        await load()
      }
    }
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
async function operate(url, action, title, needReason = false) {
  try {
    let reason = ''
    if (needReason) {
      const result = await ElMessageBox.prompt('请填写原因或处理结果（必填，最多 500 字）', title, {
        confirmButtonText: '确认提交',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (v) => (v?.trim() && v.length <= 500 ? true : '请填写 1–500 字的说明')
      })
      reason = result.value
    } else
      await ElMessageBox.confirm(`确定要${title}吗？此操作将被记录。`, title, {
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      })
    busy.value = true
    await http.post(url, { action, reason })
    ElMessage.success('操作已记录')
    await load()
  } catch {
  } finally {
    busy.value = false
  }
}
const lineOption = computed(() => ({
  color: ['#387d56'],
  tooltip: { trigger: 'axis' },
  grid: { left: 38, right: 20, top: 30, bottom: 30 },
  xAxis: {
    type: 'category',
    data: dashboard.value?.trend.map((t) => t.date.slice(5)),
    axisLine: { lineStyle: { color: '#e1e8d8' } },
    axisLabel: { color: '#9ba58e', fontSize: 10 },
    axisTick: { show: false }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: '#9ba58e', fontSize: 10 },
    splitLine: { lineStyle: { color: '#f0f3e9', type: 'dashed' } }
  },
  series: [
    {
      name: '成交笔数',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      lineStyle: { width: 3 },
      areaStyle: { color: '#e1eed7', opacity: 0.6 },
      data: dashboard.value?.trend.map((t) => t.count)
    }
  ]
}))
const pieOption = computed(() => ({
  color: ['#2e7654', '#8fb07a', '#c8d7a8', '#e9d5a0', '#b3c6be', '#e6bba0'],
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, textStyle: { fontSize: 10, color: '#8b9b7c' }, itemWidth: 8, itemHeight: 8 },
  series: [
    {
      type: 'pie',
      radius: ['43%', '67%'],
      center: ['50%', '44%'],
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 3 },
      label: { show: false },
      data: dashboard.value?.categories.map((c) => ({ name: c.name, value: c.count }))
    }
  ]
}))
watch(() => route.query.tab, load)
onMounted(load)
</script>
<template>
  <div class="container page-container">
    <div class="page-header">
      <div>
        <span class="eyebrow subtle">CAMPUS EXCHANGE / MANAGEMENT</span>
        <h1 class="page-title">让校园交易，更有秩序。</h1>
        <p class="page-subtitle">每一次认真审核，都是对校园信任的守护。</p>
      </div>
      <button class="button ghost small" @click="load" :disabled="loading">
        <Icon name="refresh" :size="15" />刷新数据
      </button>
    </div>
    <div class="workspace-layout admin-layout">
      <nav class="side-nav">
        <button
          v-for="t in tabs"
          :key="t.id"
          :class="{ active: tab === t.id }"
          @click="select(t.id)"
        >
          <Icon :name="t.icon" :size="17" />{{ t.name }}
        </button>
      </nav>
      <section class="admin-content" :class="{ 'action-loading': busy }">
        <div v-if="loading" class="panel"><el-skeleton animated :rows="10" /></div>
        <div v-else-if="failed" class="empty-state">
          <h3>数据加载失败</h3>
          <p>请检查管理员登录状态和后端服务。</p>
          <button class="button primary" @click="load">重试</button>
        </div>
        <template v-else-if="tab === 'dashboard' && dashboard"
          ><form class="panel dashboard-filters table-toolbar" @submit.prevent="load(true)">
            <b>统计区间</b
            ><el-date-picker
              v-model="filters.from"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
              style="width: 155px"
            /><el-date-picker
              v-model="filters.to"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
              style="width: 155px"
            /><button class="button primary small" type="submit">更新统计</button
            ><span class="muted">最多 90 天 · 顶部指标为累计数据</span>
          </form>
          <div class="admin-kpis">
            <div class="kpi-card">
              <label>学生用户</label><Icon name="users" :size="23" /><strong>{{
                dashboard.kpi.users
              }}</strong
              ><small>已注册学生账号</small>
            </div>
            <div class="kpi-card">
              <label>校园好物</label><Icon name="box" :size="23" /><strong>{{
                dashboard.kpi.goods
              }}</strong
              ><small>{{ dashboard.kpi.pending }} 件待审核</small>
            </div>
            <div class="kpi-card">
              <label>已完成交易</label><Icon name="receipt" :size="23" /><strong>{{
                dashboard.kpi.completed
              }}</strong
              ><small>实际确认完成的订单</small>
            </div>
            <div class="kpi-card">
              <label>累计成交额</label><Icon name="leaf" :size="23" /><strong
                ><small style="font-size: 15px; display: inline; color: inherit">¥ </small
                >{{ money(dashboard.kpi.amount) }}</strong
              ><small>{{ dashboard.kpi.reports }} 条举报待处理</small>
            </div>
          </div>
          <div class="period-summary panel">
            <h3>所选区间的交易情况</h3>
            <div>
              <span
                >发起预约 <b>{{ dashboard.period.created }}</b> 笔</span
              ><span
                >完成交易 <b>{{ dashboard.period.completed }}</b> 笔</span
              ><span
                >成交金额 <b>¥{{ money(dashboard.period.amount) }}</b></span
              ><span
                >区间预约当前已取消 <b>{{ dashboard.period.cancelled }}</b> 笔</span
              >
            </div>
            <p class="chart-note">
              区间创建的预约中，已有
              {{ dashboard.period.cohort_completed }}
              笔完成；成交笔数和金额按完成日期统计，两者时间口径不同。
            </p>
          </div>
          <div class="chart-grid">
            <div class="panel">
              <h3>区间成交趋势 · {{ dashboard.from }} 至 {{ dashboard.to }}</h3>
              <Chart :option="lineOption" />
            </div>
            <div class="panel">
              <h3>好物分类分布</h3>
              <Chart :option="pieOption" />
            </div>
          </div>
          <p class="chart-note">
            <Icon name="alert" :size="12" style="vertical-align: middle; margin-right: 4px" />{{
              dashboard.definition
            }}
          </p>
          <div class="hint-box">
            有 {{ dashboard.kpi.pending }} 件商品等待你的审核，{{
              dashboard.kpi.reports
            }}
            条举报等待处理。<button class="text-button" @click="select('goods')">
              现在去看看 →
            </button>
          </div></template
        >
        <div v-else class="panel">
          <div class="panel-heading">
            <h2>{{ tabs.find((t) => t.id === tab)?.name }}</h2>
            <span>共 {{ total }} 条 · 每页 {{ pageSize }} 条</span>
          </div>
          <ConfigManager
            v-if="['categories', 'announcements'].includes(tab)"
            :key="tab"
            :kind="tab"
            :rows="rows"
            @refresh="load"
          />
          <template v-else>
            <form class="table-toolbar" @submit.prevent="load(true)">
              <el-input
                v-if="['goods', 'users'].includes(tab)"
                v-model="filters.q"
                :placeholder="tab === 'users' ? '搜索账号 / 昵称' : '搜索商品标题'"
                clearable
              /><el-select
                v-if="['goods', 'reports', 'orders'].includes(tab)"
                v-model="filters.status"
                clearable
                placeholder="全部状态"
                ><template v-if="tab === 'goods'"
                  ><el-option
                    v-for="(name, id) in {
                      PENDING: '待审核',
                      LISTED: '在售中',
                      REJECTED: '已驳回',
                      RESERVED: '已预订',
                      SOLD: '已售出',
                      OFFLINE: '已下架'
                    }"
                    :key="id"
                    :label="name"
                    :value="id" /></template
                ><template v-else-if="tab === 'reports'"
                  ><el-option label="待处理" value="PENDING" /><el-option
                    label="已处理"
                    value="RESOLVED" /><el-option label="已驳回" value="REJECTED" /></template
                ><template v-else
                  ><el-option
                    v-for="(name, id) in {
                      PENDING: '待卖家确认',
                      CONFIRMED: '待线下交易',
                      COMPLETED: '已完成',
                      CANCELLED: '已取消'
                    }"
                    :key="id"
                    :label="name"
                    :value="id" /></template></el-select
              ><template v-if="tab === 'orders'"
                ><el-input
                  v-model="filters.buyer"
                  placeholder="买家账号 / 昵称"
                  style="width: 150px" /><el-input
                  v-model="filters.seller"
                  placeholder="卖家账号 / 昵称"
                  style="width: 150px" /><el-date-picker
                  v-model="filters.from"
                  value-format="YYYY-MM-DD"
                  placeholder="开始日期"
                  style="width: 140px" /><el-date-picker
                  v-model="filters.to"
                  value-format="YYYY-MM-DD"
                  placeholder="结束日期"
                  style="width: 140px" /></template
              ><button v-if="tab !== 'audit'" class="button primary small" type="submit">
                <Icon name="search" :size="13" />查询
              </button>
            </form>
            <div v-if="!rows.length" class="empty-state">
              <Icon name="done" :size="36" />
              <h3>暂时没有符合条件的记录</h3>
              <p>试试调整查询条件，或稍后再来。</p>
            </div>
            <div v-else class="table-scroll">
              <table v-if="tab === 'goods'" class="data-table">
                <thead>
                  <tr>
                    <th>商品 / 卖家</th>
                    <th>价格</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="g in rows" :key="g.id">
                    <td>
                      <RouterLink :to="`/goods/${g.id}`" class="table-product"
                        ><img :src="g.cover" alt="商品缩略图" />
                        <div>
                          <span>{{ g.title }}</span>
                          <div class="muted" style="font-size: 9px">
                            {{ g.seller_name }} · #{{ g.id }}
                          </div>
                        </div></RouterLink
                      >
                      <div v-if="g.reject_reason" class="reason-line">{{ g.reject_reason }}</div>
                    </td>
                    <td>¥{{ money(g.price) }}</td>
                    <td><StatusBadge :status="g.status" /></td>
                    <td>
                      <div class="table-actions">
                        <template v-if="g.status === 'PENDING'"
                          ><button
                            class="text-button"
                            @click="operate(`/admin/goods/${g.id}/review`, 'approve', '通过审核')"
                          >
                            通过审核</button
                          ><button
                            class="text-button red"
                            @click="
                              operate(`/admin/goods/${g.id}/review`, 'reject', '驳回商品', true)
                            "
                          >
                            驳回
                          </button></template
                        ><button
                          v-if="!['SOLD', 'OFFLINE'].includes(g.status)"
                          class="text-button red"
                          @click="
                            operate(`/admin/goods/${g.id}/review`, 'offline', '强制下架', true)
                          "
                        >
                          强制下架</button
                        ><RouterLink :to="`/goods/${g.id}`" class="text-button"
                          >查看详情</RouterLink
                        >
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <table v-else-if="tab === 'users'" class="data-table">
                <thead>
                  <tr>
                    <th>账号</th>
                    <th>昵称</th>
                    <th>角色</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="u in rows" :key="u.id">
                    <td>{{ u.username }}</td>
                    <td>{{ u.nickname }}</td>
                    <td>{{ u.role === 'ADMIN' ? '管理员' : '学生' }}</td>
                    <td><StatusBadge :status="u.status" /></td>
                    <td>
                      <button
                        v-if="u.role !== 'ADMIN'"
                        class="text-button"
                        @click="
                          operate(
                            `/admin/users/${u.id}/status`,
                            u.status === 'ACTIVE' ? 'disable' : 'enable',
                            u.status === 'ACTIVE' ? '禁用账号' : '恢复账号'
                          )
                        "
                      >
                        {{ u.status === 'ACTIVE' ? '禁用账号' : '恢复账号' }}</button
                      ><span v-else class="muted">受保护</span>
                    </td>
                  </tr>
                </tbody>
              </table>
              <table v-else-if="tab === 'reports'" class="data-table">
                <thead>
                  <tr>
                    <th>商品 / 举报人</th>
                    <th>举报原因</th>
                    <th>处理状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td>
                      <RouterLink :to="`/goods/${r.goods_id}`">{{ r.goods_title }}</RouterLink>
                      <div class="muted" style="font-size: 9px">
                        {{ r.reporter_name }} · {{ date(r.created_at) }}
                      </div>
                    </td>
                    <td>
                      {{ r.reason }}
                      <div v-if="r.result" class="reason-line">{{ r.result }}</div>
                    </td>
                    <td>
                      <StatusBadge
                        :status="r.status"
                        :text="r.status === 'PENDING' ? '待处理' : undefined"
                      />
                    </td>
                    <td>
                      <div v-if="r.status === 'PENDING'" class="table-actions">
                        <button
                          class="text-button"
                          @click="
                            operate(`/admin/reports/${r.id}/handle`, 'resolve', '标记已处理', true)
                          "
                        >
                          标记已处理</button
                        ><button
                          class="text-button red"
                          @click="
                            operate(`/admin/reports/${r.id}/handle`, 'reject', '驳回举报', true)
                          "
                        >
                          驳回举报
                        </button>
                      </div>
                      <span v-else class="muted">{{ date(r.handled_at) }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
              <table v-else-if="tab === 'orders'" class="data-table">
                <thead>
                  <tr>
                    <th>订单 / 商品</th>
                    <th>买家 → 卖家</th>
                    <th>金额</th>
                    <th>状态</th>
                    <th>创建时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="o in rows" :key="o.id">
                    <td>
                      <span class="muted" style="font-size: 9px"
                        >#{{ String(o.id).padStart(6, '0') }}</span
                      ><br /><RouterLink :to="`/orders/${o.id}`" class="text-button">{{
                        o.goods_title
                      }}</RouterLink>
                    </td>
                    <td>{{ o.buyer_name }} → {{ o.seller_name }}</td>
                    <td>¥{{ money(o.amount) }}</td>
                    <td>
                      <StatusBadge
                        :status="o.status"
                        :text="o.status === 'PENDING' ? '待卖家确认' : undefined"
                      />
                    </td>
                    <td>{{ date(o.created_at) }}</td>
                  </tr>
                </tbody>
              </table>
              <table v-else class="data-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>管理员</th>
                    <th>对象</th>
                    <th>操作</th>
                    <th>说明</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="a in rows" :key="a.id">
                    <td>{{ date(a.created_at) }}</td>
                    <td>{{ a.admin_name }}</td>
                    <td>{{ a.target_type }} #{{ a.target_id }}</td>
                    <td>{{ a.action }}</td>
                    <td>{{ a.detail || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <el-pagination
            v-if="total > pageSize"
            class="pagination"
            background
            :pager-count="5"
            layout="prev,pager,next,total"
            :total="total"
            :page-size="pageSize"
            v-model:current-page="currentPage"
            @current-change="load()"
          />
          <p v-if="tab === 'orders'" class="chart-note">
            管理员仅可查询订单，线下交付由买家确认。订单保留创建时的商品名称、图片和价格。
          </p>
          <p v-if="tab === 'users'" class="chart-note">
            禁用会立即撤销账号登录状态。有待线下交易的用户须先完成或取消交易，再执行禁用。
          </p>
          <p v-if="tab === 'reports'" class="chart-note">
            处理举报记录后，如需下架商品，请在商品审核中填写原因并下架，两个操作分别留痕。
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

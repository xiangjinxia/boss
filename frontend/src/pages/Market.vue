<script setup>
import { ref, reactive, onMounted } from 'vue'
import { http, task } from '../services/api'
import Icon from '../components/Icon.vue'
import GoodsCard from '../components/GoodsCard.vue'
const categories = ref([]),
  stats = ref({}),
  items = ref([]),
  total = ref(0),
  loading = ref(false),
  error = ref(false),
  advanced = ref(false)
const filters = reactive({
  q: '',
  category: undefined,
  min: undefined,
  max: undefined,
  days: undefined,
  sort: 'newest',
  page: 1,
  size: 12
})
async function load(reset = false) {
  if (reset) filters.page = 1
  loading.value = true
  error.value = false
  try {
    const data = await http.get('/goods', { params: filters })
    items.value = data.items
    total.value = data.total
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
function clearSearch() {
  filters.q = ''
  load(true)
}
function category(id) {
  filters.category = id
  load(true)
}
function clear() {
  Object.assign(filters, {
    q: '',
    category: undefined,
    min: undefined,
    max: undefined,
    days: undefined,
    sort: 'newest'
  })
  load(true)
}
onMounted(async () => {
  await load()
  await task(async () => {
    ;[categories.value, stats.value] = await Promise.all([
      http.get('/categories'),
      http.get('/public/stats')
    ])
  })
})
</script>
<template>
  <div class="market-page container">
    <section class="hero">
      <div class="hero-copy">
        <div class="eyebrow"><span></span>旧物新生 · 校园好物循环计划</div>
        <h1>
          让闲置，<br />遇见<span>新的故事。</span
          ><svg class="hero-underline" viewBox="0 0 290 15">
            <path
              d="M3 10 Q120 -3 285 9"
              fill="none"
              stroke="currentColor"
              stroke-width="5"
              stroke-linecap="round"
            />
          </svg>
        </h1>
        <p>书本翻到下一页，好物交给下一个人。<br />在这里，发现属于你的校园小确幸。</p>
        <a href="#market" class="button dark">逛逛校园集市<Icon name="right" :size="17" /></a>
        <div class="hero-proof">
          <div class="avatar-stack">
            <span>林</span><span>晴</span><span>周</span><span>陈</span>
          </div>
          <div>
            <strong>{{ stats.users || '—' }} 位</strong>同学的闲置新旅程<small
              ><Icon name="shield" :size="12" />先审核，再上架 · 校内见面交易</small
            >
          </div>
        </div>
      </div>
      <div class="hero-art" aria-label="校园好物插画">
        <div class="art-orbit"></div>
        <div class="art-dots"></div>
        <div class="art-spark art-spark-one">✳</div>
        <div class="art-spark art-spark-two">✦</div>
        <div class="hero-sticker sticker-top">
          <span class="sticker-icon"><Icon name="leaf" :size="20" /></span>闲置不闲置，快乐再传递
        </div>
        <div class="art-product art-camera">
          <img :src="'/samples/camera.svg'" alt="复古相机" />
          <div><b>记录新的风景</b><span>好物，值得被继续喜欢</span></div>
        </div>
        <div class="art-product art-books"><img :src="'/samples/books.svg'" alt="书本" /></div>
        <div class="hero-sticker sticker-bottom"><span>♻</span>给好物第二次心动</div>
        <div class="art-label">LESS WASTE.<br />MORE STORIES.</div>
      </div>
    </section>
    <section class="trust-strip">
      <div>
        <Icon name="shield" /><span><b>审核上架</b><small>每一份闲置，都被认真对待</small></span>
      </div>
      <div>
        <Icon name="pin" /><span><b>校园面交</b><small>当面看看，安心带走</small></span>
      </div>
      <div>
        <Icon name="message" /><span><b>先聊再买</b><small>留言问清楚，交易更从容</small></span>
      </div>
      <div>
        <Icon name="leaf" /><span><b>循环生活</b><small>让美好继续，给生活减负</small></span>
      </div>
    </section>
    <section id="market" class="market-section">
      <div class="section-heading">
        <div>
          <div class="eyebrow subtle">THE CAMPUS MARKET</div>
          <h2>校园里的宝藏闲置<span class="heading-dot">.</span></h2>
          <p>刚刚上架的好物，也许正是你在寻找的。</p>
        </div>
        <span class="live-tag"><span></span>{{ total }} 件好物正在等待新主人</span>
      </div>
      <form class="search-bar" @submit.prevent="load(true)">
        <Icon name="search" :size="21" /><input
          v-model="filters.q"
          placeholder="搜一搜教材、相机、自行车……"
          aria-label="搜索商品"
          maxlength="80"
        /><button
          class="search-clear icon-button"
          v-if="filters.q"
          type="button"
          @click="clearSearch"
          aria-label="清除搜索"
        >
          <Icon name="close" :size="17" /></button
        ><button class="button primary" type="submit">
          搜索好物<Icon name="right" :size="16" />
        </button>
      </form>
      <div class="category-row">
        <button
          @click="category(undefined)"
          class="category-chip"
          :class="{ selected: !filters.category }"
        >
          <Icon name="sparkles" :size="18" />全部好物</button
        ><button
          v-for="c in categories"
          :key="c.id"
          @click="category(c.id)"
          class="category-chip"
          :class="{ selected: filters.category === c.id }"
        >
          <Icon :name="c.icon" :size="18" />{{ c.name }}
        </button>
      </div>
      <div class="list-toolbar">
        <span
          >发现 <b>{{ total }}</b> 件校园好物</span
        >
        <div>
          <el-select
            v-model="filters.sort"
            @change="load(true)"
            aria-label="排序"
            style="width: 128px"
            ><el-option label="最新发布" value="newest" /><el-option
              label="价格从低到高"
              value="price_asc" /><el-option label="价格从高到低" value="price_desc" /></el-select
          ><button
            class="filter-button"
            :class="{ active: advanced }"
            @click="advanced = !advanced"
          >
            <Icon name="filter" :size="16" />筛选
          </button>
        </div>
      </div>
      <form v-if="advanced" class="advanced-filters" @submit.prevent="load(true)">
        <label
          >价格区间
          <div class="range">
            <el-input-number
              v-model="filters.min"
              :min="0"
              :controls="false"
              placeholder="最低价"
            /><span>—</span
            ><el-input-number
              v-model="filters.max"
              :min="0"
              :controls="false"
              placeholder="最高价"
            /></div></label
        ><label
          >发布时间<el-select
            v-model="filters.days"
            placeholder="不限时间"
            clearable
            style="width: 150px"
            ><el-option label="最近 24 小时" :value="1" /><el-option
              label="最近 7 天"
              :value="7" /><el-option label="最近 30 天" :value="30" /></el-select></label
        ><button class="button primary" type="submit">应用筛选</button
        ><button class="button ghost" type="button" @click="clear">重置</button>
      </form>
      <div v-if="loading" class="goods-grid">
        <div class="skeleton-card" v-for="i in 8" :key="i"><el-skeleton animated :rows="4" /></div>
      </div>
      <div v-else-if="error" class="empty-state">
        <Icon name="alert" :size="36" />
        <h3>暂时没能加载好物</h3>
        <p>请确认服务正常，或稍后再试。</p>
        <button class="button primary" @click="load()">重新加载</button>
      </div>
      <div v-else-if="!items.length" class="empty-state">
        <Icon name="search" :size="40" />
        <h3>还没找到这件好物</h3>
        <p>试试别的关键词，或放宽筛选条件。</p>
        <button class="button ghost" @click="clear">看看全部好物</button>
      </div>
      <div v-else class="goods-grid"><GoodsCard v-for="g in items" :key="g.id" :goods="g" /></div>
      <el-pagination
        v-if="total > filters.size && !loading"
        class="pagination"
        background
        :pager-count="5"
        layout="prev, pager, next"
        :total="total"
        :page-size="filters.size"
        v-model:current-page="filters.page"
        @current-change="load()"
      />
    </section>
    <section class="publish-banner">
      <div>
        <span class="eyebrow">GOOD THINGS DESERVE A SECOND CHANCE</span>
        <h2>你的闲置，是别人的心头好。</h2>
        <p>整理一下宿舍，让好物开始一段新的旅程。</p>
      </div>
      <RouterLink to="/publish" class="button dark"
        ><Icon name="plus" :size="19" />发布我的闲置</RouterLink
      ><Icon class="banner-leaf" name="leaf" :size="145" />
    </section>
  </div>
</template>

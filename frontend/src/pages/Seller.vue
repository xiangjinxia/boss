<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { http, date } from '../services/api'
import Icon from '../components/Icon.vue'
import GoodsCard from '../components/GoodsCard.vue'
const route = useRoute(),
  data = ref(null),
  reviews = ref({ items: [], total: 0 }),
  page = ref(1),
  reviewPage = ref(1),
  loading = ref(true),
  failed = ref(false)
async function load() {
  loading.value = true
  failed.value = false
  try {
    data.value = await http.get(`/sellers/${route.params.id}`, {
      params: { page: page.value, size: 8 }
    })
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
async function loadReviews() {
  reviews.value = await http.get(`/sellers/${route.params.id}/reviews`, {
    params: { page: reviewPage.value, size: 10 }
  })
}
onMounted(async () => {
  await load()
  if (data.value) await loadReviews()
})
</script>
<template>
  <div class="container page-container seller-page">
    <div class="breadcrumb">
      <RouterLink to="/">校园集市</RouterLink><Icon name="chevron" :size="13" />同学的闲置小铺
    </div>
    <div v-if="loading && !data" class="panel"><el-skeleton animated :rows="8" /></div>
    <div v-else-if="failed || !data" class="empty-state">
      <h2>暂时无法查看这个小铺</h2>
      <p>账号可能已停用，或服务暂时不可用。</p>
      <button class="button primary" @click="load">重新加载</button>
    </div>
    <template v-else>
      <section class="personal-cover seller-cover">
        <div class="profile-avatar">
          <img v-if="data.profile.avatar" :src="data.profile.avatar" alt="卖家头像" /><span
            v-else
            >{{ data.profile.nickname.slice(0, 1) }}</span
          >
        </div>
        <div>
          <div class="eyebrow subtle">THE CAMPUS NEIGHBOR</div>
          <h1>{{ data.profile.nickname }}的闲置小铺</h1>
          <p>{{ data.profile.bio || '欢迎来看看我的校园闲置。' }}</p>
          <small class="muted">加入于 {{ date(data.profile.created_at).slice(0, 10) }}</small>
        </div>
        <div class="personal-stats">
          <div>
            <strong>{{ data.stats.listed }}</strong
            ><small>在售好物</small>
          </div>
          <div>
            <strong>{{ data.stats.completed }}</strong
            ><small>完成交易</small>
          </div>
          <div>
            <strong>{{ data.stats.rating || '—' }}</strong
            ><small>{{
              data.stats.review_count ? `${data.stats.review_count} 条成交评价` : '暂无成交评价'
            }}</small>
          </div>
        </div>
      </section>
      <section class="market-section">
        <div class="section-heading">
          <div>
            <h2>正在寻找新主人的好物</h2>
            <p>仅展示这位同学当前在售的商品。</p>
          </div>
          <span class="live-tag">{{ data.stats.listed }} 件在售</span>
        </div>
        <div v-if="!data.goods.items.length" class="empty-state">
          <Icon name="box" :size="35" />
          <h3>暂时没有在售商品</h3>
          <RouterLink to="/" class="button ghost">逛逛校园集市</RouterLink>
        </div>
        <div v-else class="goods-grid">
          <GoodsCard v-for="g in data.goods.items" :key="g.id" :goods="g" />
        </div>
        <el-pagination
          v-if="data.goods.total > 8"
          class="pagination"
          background
          layout="prev,pager,next"
          :total="data.goods.total"
          :page-size="8"
          v-model:current-page="page"
          @current-change="load"
        />
      </section>
      <section class="panel review-section">
        <h2 class="panel-title">
          交易后的真实声音<span class="muted">{{ reviews.total }} 条评价</span>
        </h2>
        <p class="chart-note">
          评价来自已完成订单的买家，每笔交易只能评价一次。暂无评价的同学不会被赋予默认高分。
        </p>
        <div v-if="!reviews.items.length" class="empty-state">
          <Icon name="message" :size="32" />
          <h3>还没有成交评价</h3>
          <p>先留言了解商品，校内面交时仔细检查实物。</p>
        </div>
        <article v-for="r in reviews.items" :key="r.id" class="review-item">
          <header>
            <span class="mini-avatar">{{ r.buyer_name.slice(0, 1) }}</span
            ><b>{{ r.buyer_name }}</b
            ><el-rate :model-value="r.rating" disabled /><time>{{ date(r.created_at) }}</time>
          </header>
          <p>{{ r.content }}</p>
          <RouterLink :to="`/goods/${r.goods_id}`" class="muted"
            >成交商品：{{ r.goods_title }}</RouterLink
          >
        </article>
        <el-pagination
          v-if="reviews.total > 10"
          class="pagination"
          background
          layout="prev,pager,next"
          :total="reviews.total"
          :page-size="10"
          v-model:current-page="reviewPage"
          @current-change="loadReviews"
        />
      </section>
    </template>
  </div>
</template>

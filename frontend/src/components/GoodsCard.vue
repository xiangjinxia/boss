<script setup>
import Icon from './Icon.vue'
import StatusBadge from './StatusBadge.vue'
import { money } from '../services/api'
defineProps({ goods: Object, status: Boolean })
</script>
<template>
  <RouterLink :to="`/goods/${goods.id}`" class="goods-card">
    <div class="goods-image">
      <img :src="goods.cover" :alt="goods.title" loading="lazy" /><span class="condition">{{
        goods.condition_level
      }}</span
      ><span class="card-arrow"><Icon name="arrow" :size="18" /></span>
    </div>
    <div class="goods-body">
      <div class="card-category">
        {{ goods.category_name }} <StatusBadge v-if="status" :status="goods.status" />
      </div>
      <h3>{{ goods.title }}</h3>
      <div class="goods-bottom">
        <div class="price"><small>¥</small>{{ money(goods.price) }}</div>
        <span class="favorite-count"
          ><Icon name="heart" :size="14" />{{ goods.favorite_count }}</span
        >
      </div>
      <div class="seller-line">
        <span class="mini-avatar">{{ goods.seller_name?.slice(0, 1) }}</span
        >{{ goods.seller_name }}<span class="campus-tag">校内交易</span>
      </div>
    </div>
  </RouterLink>
</template>

<script setup>
import Icon from '../components/Icon.vue'
</script>
<template>
  <div class="container page-container">
    <div class="empty-state not-found">
      <h1>404</h1>
      <h3>这条校园小路还没有开放</h3>
      <p>回到集市，继续寻找你的下一份心动吧。</p>
      <RouterLink to="/" class="button primary"
        >返回校园集市<Icon name="right" :size="16"
      /></RouterLink>
    </div>
  </div>
</template>

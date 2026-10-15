<script setup>
import { labels } from '../services/api'
defineProps({ status: String, text: String })
</script>
<template>
  <span class="badge" :class="status?.toLowerCase()"
    ><span class="status-dot"></span>{{ text || labels[status] || status }}</span
  >
</template>

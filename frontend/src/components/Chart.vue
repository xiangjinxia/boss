<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const props = defineProps({ option: Object }),
  el = ref(null)
let chart, observer
onMounted(() => {
  chart = echarts.init(el.value)
  chart.setOption(props.option)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(el.value)
})
watch(
  () => props.option,
  (o) => chart?.setOption(o),
  { deep: true }
)
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>
<template><div class="chart" ref="el"></div></template>

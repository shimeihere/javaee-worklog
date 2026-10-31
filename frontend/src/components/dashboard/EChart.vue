<template>
  <div ref="container" class="echart" :style="{ height }" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import echarts from '@/utils/echarts'

const props = defineProps({
  option: { type: Object, default: null },
  height: { type: String, default: '240px' }
})

const container = ref(null)

let chart = null
let observer = null

onMounted(() => {
  if (!container.value) {
    return
  }
  chart = echarts.init(container.value, null, { renderer: 'canvas' })
  if (props.option) {
    chart.setOption(props.option, true)
  }
  // 卡片宽度随窗口/侧栏折叠变化，用 ResizeObserver 保证图表自适应
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value)
})

watch(
  () => props.option,
  (option) => {
    if (chart && option) {
      chart.setOption(option, true)
    }
  },
  { deep: true }
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echart {
  width: 100%;
}
</style>

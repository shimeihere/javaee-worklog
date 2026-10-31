<template>
  <section class="placeholder">
    <div class="placeholder__card">
      <span class="placeholder__icon">
        <el-icon><component :is="icon" /></el-icon>
      </span>
      <h2>{{ title }}</h2>
      <p>{{ desc }}</p>
      <el-button text @click="router.push({ name: 'workbench' })">← 返回工作台</el-button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const title = computed(() => route.meta?.title || '模块建设中')
const desc = computed(() => route.meta?.desc || '该模块将在后续阶段开放。')
const icon = computed(() => route.meta?.icon || 'Grid')
</script>

<style scoped>
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 64px - 70px);
}

.placeholder__card {
  width: min(460px, 100%);
  padding: 46px 40px 38px;
  text-align: center;
  background: var(--wl-card);
  border-radius: 16px;
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 24px 56px -34px rgba(63, 74, 90, 0.5);
  animation: rise 0.6s var(--wl-spring) both;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.placeholder__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 16px;
  border-radius: 14px;
  background: rgba(122, 106, 147, 0.1);
  color: var(--wl-violet);
  font-size: 20px;
}

.placeholder__card h2 {
  margin: 0;
  color: var(--wl-primary);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1.5px;
}

.placeholder__card p {
  margin: 12px 0 22px;
  color: rgba(40, 38, 36, 0.5);
  font-size: 13px;
  line-height: 1.9;
}
</style>

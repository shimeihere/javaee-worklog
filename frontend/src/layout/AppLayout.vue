<template>
  <div class="app-shell">
    <AppSidebar :collapsed="collapsed" />

    <div class="app-shell__body">
      <AppHeader :collapsed="collapsed" @toggle="toggleSidebar" />

      <main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="$route.name" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'

const COLLAPSE_KEY = 'worklog_sidebar_collapsed'

const collapsed = ref(false)

onMounted(() => {
  collapsed.value = localStorage.getItem(COLLAPSE_KEY) === '1'
})

function toggleSidebar() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}
</script>

<style scoped>
.app-shell {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 100vh;
  background: var(--wl-bg);
}

.app-shell__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
  padding: 26px 30px 44px;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition:
    opacity 0.3s var(--wl-spring),
    transform 0.3s var(--wl-spring);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 720px) {
  .app-main {
    padding: 18px 16px 36px;
  }
}
</style>

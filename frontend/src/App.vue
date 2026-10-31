<template>
  <router-view v-slot="{ Component }">
    <transition name="page" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<script setup>
// 根组件只负责页面切换动效，具体布局在各视图内实现
</script>

<style>
.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.34s cubic-bezier(0.34, 1.56, 0.64, 1),
    transform 0.34s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.995);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.995);
}
</style>

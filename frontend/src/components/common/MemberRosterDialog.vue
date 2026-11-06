<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="400px"
    class="roster-dialog"
    append-to-body
    align-center
  >
    <ul v-if="members.length" class="roster">
      <li v-for="member in members" :key="member.id" class="roster__item">
        <span class="roster__avatar">{{ member.realName?.slice(0, 1) }}</span>

        <button
          v-if="clickable && member.id"
          type="button"
          class="roster__name roster__name--link"
          @click="select(member)"
        >
          {{ member.realName }}
        </button>
        <span v-else class="roster__name">{{ member.realName }}</span>

        <span class="roster__role">{{ member.roleName }}</span>
      </li>
    </ul>

    <p v-else class="roster__empty">{{ loading ? '加载中…' : '暂无成员' }}</p>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '成员名单' },
  members: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 姓名是否可点（点了再叠加明细弹窗） */
  clickable: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'select'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

function select(member) {
  emit('select', member)
}
</script>

<style scoped>
.roster {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 360px;
  overflow-y: auto;
}

.roster__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 10px;
  transition: var(--wl-motion);
}

.roster__item:hover {
  background: rgba(63, 74, 90, 0.04);
  transform: translateY(-1px);
}

.roster__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(63, 74, 90, 0.08);
  color: var(--wl-primary);
  font-size: 12px;
  font-weight: 600;
}

.roster__name {
  flex: 1;
  color: var(--wl-text);
  font-size: 13px;
}

/* 可点击姓名：下钻到个人明细 */
.roster__name--link {
  padding: 0;
  border: none;
  background: none;
  color: var(--wl-primary);
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  border-bottom: 1px dashed rgba(63, 74, 90, 0.3);
  transition: var(--wl-motion);
}

.roster__name--link:hover {
  color: var(--wl-violet);
  border-bottom-color: rgba(122, 106, 147, 0.5);
}

.roster__role {
  color: rgba(40, 38, 36, 0.4);
  font-size: 11px;
}

.roster__empty {
  padding: 28px 0;
  margin: 0;
  color: rgba(40, 38, 36, 0.34);
  font-size: 12px;
  text-align: center;
}
</style>

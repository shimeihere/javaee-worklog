<template>
  <main class="login">
    <section class="login__card">
      <header class="brand">
        <h1 class="brand__title">Worklog</h1>
      </header>

      <el-form
        ref="formRef"
        class="form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            size="large"
            placeholder="Username"
            autocomplete="username"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            placeholder="Password"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button
          class="form__submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="handleSubmit"
        >
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </el-button>
      </el-form>

      <section class="demo">
        <div class="demo__head">
          <span class="demo__line" />
          <span class="demo__text">Demo accounts</span>
          <span class="demo__line" />
        </div>

        <div class="demo__list">
          <button
            v-for="item in demoAccounts"
            :key="item.username"
            class="chip"
            type="button"
            @click="fillDemo(item)"
          >
            <span class="chip__role">{{ item.role }}</span>
            <span class="chip__name">{{ item.username }}</span>
          </button>
        </div>
      </section>
    </section>

    <footer class="foot">Worklog · v1.0</footer>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { loginApi } from '@/api/auth'
import { getMessages } from '@/api/message'
import { setToken, setUser } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
  password: [
    { required: true, message: 'Password is required', trigger: 'blur' },
    { min: 6, max: 32, message: 'Password must be 6-32 characters', trigger: 'blur' }
  ]
}

/** 四个角色各一个演示账号（密码统一 123456） */
const demoAccounts = [
  { role: 'Executive', username: 'boss', password: '123456' },
  { role: 'Admin', username: 'admin', password: '123456' },
  { role: 'Leader', username: 'liyue', password: '123456' },
  { role: 'Employee', username: 'wangfan', password: '123456' }
]

function fillDemo(account) {
  form.username = account.username
  form.password = account.password
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (loading.value) {
    return
  }

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    const res = await loginApi({
      username: form.username.trim(),
      password: form.password
    })

    setToken(res.data.token)
    setUser(res.data.user)

    ElMessage.success(`欢迎回来，${res.data.user.realName}`)

    // 登录后如果有未读催报，右下角温和提醒一次
    checkRemind()

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch {
    // 错误提示已由 Axios 拦截器统一处理，这里只负责结束 loading
  } finally {
    loading.value = false
  }
}

async function checkRemind() {
  try {
    const res = await getMessages({ page: 1, size: 5, isRead: 0, msgType: 1 })
    const total = res.data?.total || 0
    if (total <= 0) {
      return
    }

    ElNotification({
      title: '您有新的催报提醒',
      message: `共 ${total} 条未读催报，点击查看并尽快提交日报`,
      type: 'warning',
      position: 'bottom-right',
      duration: 8000,
      offset: 24,
      onClick: () => router.push({ name: 'messages' })
    })
  } catch {
    /* 提醒失败不影响登录 */
  }
}
</script>

<style scoped>
.login {
  position: relative;
  z-index: 1;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 26px;
  padding: 56px 20px 40px;
}

/* 纯白居中卡片：12px 白边 + 两层悬浮阴影 + 弹簧上浮 */
.login__card {
  width: min(420px, 100%);
  padding: 42px 40px 30px;
  background: var(--wl-card);
  border-radius: var(--wl-radius-card);
  box-shadow:
    0 0 0 12px rgba(255, 255, 255, 0.72),
    0 24px 64px -28px rgba(63, 74, 90, 0.34),
    0 2px 6px rgba(63, 74, 90, 0.04);
  transition:
    transform 0.55s var(--wl-spring),
    box-shadow 0.55s var(--wl-spring);
  animation: card-in 0.7s var(--wl-spring) both;
}

.login__card:hover {
  transform: translateY(-6px) scale(1.008);
  box-shadow:
    0 0 0 12px rgba(255, 255, 255, 0.92),
    0 40px 80px -30px rgba(63, 74, 90, 0.4),
    0 4px 10px rgba(63, 74, 90, 0.06);
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* ---------------- 品牌区（纯文字 Worklog） ---------------- */
.brand {
  margin-bottom: 32px;
  text-align: center;
}

.brand__title {
  margin: 0;
  color: #1a1d20;
  font-family: 'Inter', 'HarmonyOS Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

/* ---------------- 表单 ---------------- */
.form__submit {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  font-size: 15px;
  letter-spacing: 4px;
  background: var(--wl-primary);
  border-color: var(--wl-primary);
  box-shadow: 0 14px 26px -16px rgba(63, 74, 90, 0.95);
}

.form__submit:hover {
  background: #4a5668;
  border-color: #4a5668;
  box-shadow: 0 18px 30px -16px rgba(63, 74, 90, 0.95);
}

/* ---------------- 演示账号 ---------------- */
.demo {
  margin-top: 28px;
}

.demo__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.demo__line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(63, 74, 90, 0.14), transparent);
}

.demo__text {
  white-space: nowrap;
  color: var(--wl-muted);
  font-size: 11px;
  letter-spacing: 1px;
}

.demo__list {
  display: flex;
  gap: 8px;
}

.chip {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 9px 6px;
  border: 1px solid rgba(63, 74, 90, 0.12);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition:
    transform 0.4s var(--wl-spring),
    border-color 0.3s var(--wl-ease),
    box-shadow 0.4s var(--wl-spring);
}

.chip:hover {
  transform: translateY(-3px);
  border-color: rgba(122, 106, 147, 0.45);
  box-shadow: 0 12px 22px -14px rgba(122, 106, 147, 0.85);
}

.chip:active {
  transform: translateY(-1px) scale(0.98);
}

.chip__role {
  color: var(--wl-violet);
  font-size: 11px;
}

.chip__name {
  color: var(--wl-text);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.foot {
  position: relative;
  z-index: 1;
  margin: 0;
  color: rgba(40, 38, 36, 0.34);
  font-size: 11px;
  letter-spacing: 0.5px;
}

@media (max-width: 480px) {
  .login__card {
    padding: 34px 24px 24px;
  }

  .demo__list {
    flex-wrap: wrap;
  }

  .chip {
    flex: 1 1 30%;
  }
}
</style>

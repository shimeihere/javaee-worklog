<template>
  <div class="profile">
    <header class="profile__head">
      <h1>个人中心</h1>
      <p>查看账号信息，或修改登录密码</p>
    </header>

    <section class="card info">
      <span class="info__avatar">{{ info.realName?.slice(0, 1) || 'U' }}</span>

      <div class="info__body">
        <h2>{{ info.realName || '—' }}</h2>
        <p>{{ info.deptName || '未分配部门' }} · {{ info.roleName || '—' }}</p>
      </div>

      <dl class="info__grid">
        <div>
          <dt>登录账号</dt>
          <dd>{{ info.username || '—' }}</dd>
        </div>
        <div>
          <dt>真实姓名</dt>
          <dd>{{ info.realName || '—' }}</dd>
        </div>
        <div>
          <dt>所属部门</dt>
          <dd>{{ info.deptName || '—' }}</dd>
        </div>
        <div>
          <dt>角色</dt>
          <dd>{{ info.roleName || '—' }}</dd>
        </div>
      </dl>
    </section>

    <section class="card pwd">
      <header class="pwd__head">
        <h3>修改密码</h3>
        <span>修改后需要用新密码重新登录</span>
      </header>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="pwd__form">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>

      <footer class="pwd__foot">
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存新密码</el-button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePasswordApi, meApi } from '@/api/auth'
import { clearAuth, getUser } from '@/utils/auth'

const router = useRouter()
const formRef = ref(null)
const saving = ref(false)

const info = reactive({
  username: '',
  realName: '',
  deptName: '',
  roleName: ''
})

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码需为 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function loadProfile() {
  // 先用本地缓存兜底，再拉一次最新信息
  Object.assign(info, getUser() || {})
  try {
    const res = await meApi()
    Object.assign(info, res.data || {})
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  saving.value = true
  try {
    const res = await changePasswordApi({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    ElMessage.success(res.message || '密码已修改')
    // 密码变更后强制重新登录
    setTimeout(() => {
      clearAuth()
      router.replace({ name: 'login' })
    }, 800)
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 820px;
  margin: 0 auto;
}

.profile__head {
  padding: 4px 2px 0;
}

.profile__head h1 {
  margin: 0;
  color: var(--wl-text);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
}

.profile__head p {
  margin: 8px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
}

.card {
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

/* ---------------- 基本信息 ---------------- */
.info {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding: 22px;
}

.info__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: linear-gradient(145deg, #3f4a5a, #7a6a93);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  box-shadow: 0 14px 28px -18px rgba(63, 74, 90, 0.9);
}

.info__body h2 {
  margin: 0;
  color: var(--wl-text);
  font-size: 18px;
  font-weight: 600;
}

.info__body p {
  margin: 6px 0 0;
  color: rgba(40, 38, 36, 0.45);
  font-size: 12px;
}

.info__grid {
  flex: 1 1 100%;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin: 6px 0 0;
}

.info__grid div {
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(63, 74, 90, 0.035);
}

.info__grid dt {
  color: rgba(40, 38, 36, 0.38);
  font-size: 11px;
}

.info__grid dd {
  margin: 6px 0 0;
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
}

/* ---------------- 修改密码 ---------------- */
.pwd {
  padding: 20px 22px 18px;
}

.pwd__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(63, 74, 90, 0.06);
}

.pwd__head h3 {
  margin: 0;
  color: var(--wl-text);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.8px;
}

.pwd__head span {
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

.pwd__form {
  max-width: 360px;
}

.pwd__foot {
  display: flex;
  justify-content: flex-end;
  padding-top: 6px;
}
</style>

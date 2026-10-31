<template>
  <div class="settings">
    <header class="settings__head">
      <div>
        <h1>系统设置</h1>
        <p>部门、用户与项目的基础维护，仅管理员可见</p>
      </div>
    </header>

    <section v-if="!isAdmin" class="denied">
      <span class="denied__icon">
        <el-icon><Lock /></el-icon>
      </span>
      <h3>该页面仅管理员可访问</h3>
      <p>如需调整组织架构或账号状态，请联系管理员。</p>
      <el-button text @click="router.push({ name: 'workbench' })">← 返回工作台</el-button>
    </section>

    <section v-else class="card">
      <el-tabs v-model="tab" class="tabs">
        <!-- ================= 部门 ================= -->
        <el-tab-pane label="部门管理" name="dept">
          <div class="pane-head">
            <span class="pane-head__hint">左侧选部门，右侧看该部门成员</span>
            <el-button type="primary" :icon="Plus" @click="openCreateDept">新增部门</el-button>
          </div>

          <div class="dept-split">
            <aside class="dept-tree">
              <el-tree
                :data="deptTree"
                node-key="id"
                default-expand-all
                highlight-current
                :expand-on-click-node="false"
                :props="{ label: 'deptName', children: 'children' }"
                @node-click="selectDept"
              />
            </aside>

            <section class="dept-members">
              <header class="dept-members__head">
                <b>{{ activeDept?.deptName || '选择一个部门' }}</b>
                <span class="dept-members__count">{{ deptMembers.length }} 人</span>
                <el-button
                  v-if="activeDept"
                  link
                  type="primary"
                  class="dept-members__edit"
                  @click="openEditDept(activeDept)"
                >
                  编辑部门
                </el-button>
              </header>

              <el-table v-loading="deptMemberLoading" :data="deptMembers" class="table">
                <el-table-column prop="realName" label="姓名" min-width="120" />
                <el-table-column prop="roleName" label="角色" width="130" />
              </el-table>

              <p v-if="!activeDept" class="dept-hint">点左边任意部门查看成员</p>
            </section>
          </div>
        </el-tab-pane>

        <!-- ================= 用户 ================= -->
        <el-tab-pane label="用户管理" name="user">
          <div class="pane-head">
            <div class="pane-filters">
              <el-select v-model="userQuery.deptId" class="filter" placeholder="全部部门" clearable @change="searchUsers">
                <el-option
                  v-for="dept in deptOptions"
                  :key="dept.id"
                  :label="dept.deptName"
                  :value="dept.id"
                />
              </el-select>
              <el-select v-model="userQuery.role" class="filter" placeholder="全部角色" clearable @change="searchUsers">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="部门负责人" value="DEPT_LEADER" />
                <el-option label="员工" value="EMPLOYEE" />
              </el-select>
              <el-input
                v-model="userQuery.keyword"
                class="filter filter--kw"
                placeholder="账号或姓名"
                clearable
                :prefix-icon="Search"
                @keyup.enter="searchUsers"
              />
              <el-button @click="searchUsers">查询</el-button>
            </div>

            <el-button type="primary" :icon="Plus" @click="openCreateUser">新增用户</el-button>
          </div>

          <el-table v-loading="userLoading" :data="users" class="table">
            <el-table-column prop="realName" label="姓名" min-width="120" />
            <el-table-column prop="username" label="账号" min-width="130" />
            <el-table-column prop="roleName" label="角色" width="130" />
            <el-table-column prop="deptName" label="部门" min-width="150" />
            <el-table-column label="启用状态" width="140" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status === 0"
                  :loading="statusUpdating === row.id"
                  inline-prompt
                  active-text="启用"
                  inactive-text="禁用"
                  :before-change="() => confirmToggleUser(row)"
                  @change="(value) => applyToggleUser(row, value)"
                />
              </template>
            </el-table-column>
          </el-table>

          <footer class="foot">
            <el-pagination
              v-model:current-page="userQuery.page"
              v-model:page-size="userQuery.size"
              :total="userTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadUsers"
              @size-change="onUserSizeChange"
            />
          </footer>
        </el-tab-pane>

        <!-- ================= 项目 ================= -->
        <el-tab-pane label="项目管理" name="project">
          <div class="pane-head">
            <span class="pane-head__hint">创建项目时必须指定负责人，负责人可兼任部门负责人</span>
            <el-button type="primary" :icon="Plus" @click="openCreateProject">新建项目</el-button>
          </div>

          <el-table v-loading="projectLoading" :data="projects" class="table">
            <el-table-column prop="projectName" label="项目名称" min-width="220">
              <template #default="{ row }">
                <button class="name-link" type="button" @click="openProjectRoster(row)">
                  {{ row.projectName }}
                </button>
              </template>
            </el-table-column>
            <el-table-column prop="leaderName" label="负责人" width="120" />
            <el-table-column prop="deptName" label="所属部门" min-width="140" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span class="status" :class="row.status === 0 ? 'is-on' : 'is-off'">
                  {{ row.status === 0 ? '进行中' : '已归档' }}
                </span>
              </template>
            </el-table-column>
          </el-table>

          <footer class="foot">
            <el-pagination
              v-model:current-page="projectQuery.page"
              v-model:page-size="projectQuery.size"
              :total="projectTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadProjects"
              @size-change="onProjectSizeChange"
            />
          </footer>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 部门弹窗 -->
    <el-dialog
      v-model="deptDialogVisible"
      :title="deptForm.id ? '编辑部门' : '新增部门'"
      width="420px"
      append-to-body
      @closed="resetDeptForm"
    >
      <el-form :model="deptForm" label-position="top">
        <el-form-item label="上级部门">
          <el-select v-model="deptForm.parentId" class="fill" placeholder="选择上级">
            <el-option label="— 顶级部门（公司）—" :value="0" />
            <el-option
              v-for="root in deptTree"
              :key="root.id"
              :label="root.deptName"
              :value="root.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model="deptForm.deptName" maxlength="64" placeholder="例如：质量保障部" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="deptForm.sortOrder" :min="0" :max="999" controls-position="right" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="deptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="deptSaving" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>

    <!-- 项目弹窗 -->
    <el-dialog v-model="projectDialogVisible" title="新建项目" width="480px" append-to-body>
      <el-form :model="projectForm" label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="projectForm.projectName" maxlength="100" placeholder="例如：客户数据中台重构" />
        </el-form-item>
        <el-form-item label="项目负责人" required>
          <el-select v-model="projectForm.leaderId" class="fill" filterable placeholder="选择负责人">
            <el-option
              v-for="leader in leaderOptions"
              :key="leader.id"
              :label="`${leader.realName}（${leader.roleName}）`"
              :value="leader.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="projectForm.deptId" class="fill" placeholder="选择部门">
            <el-option
              v-for="dept in deptOptions"
              :key="dept.id"
              :label="dept.deptName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input
            v-model="projectForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="一句话说明项目目标"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="projectDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="projectSaving" @click="saveProject">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="userDialogVisible" title="新增用户" width="480px" append-to-body>
      <el-form :model="userForm" label-position="top">
        <el-form-item label="登录账号" required>
          <el-input v-model="userForm.username" maxlength="50" placeholder="例如：zhangsan" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            maxlength="32"
            placeholder="6-32 位，创建后请告知本人"
          />
        </el-form-item>
        <el-form-item label="真实姓名" required>
          <el-input v-model="userForm.realName" maxlength="50" placeholder="例如：张三" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="userForm.role" class="fill">
            <el-option label="员工" value="EMPLOYEE" />
            <el-option label="部门负责人" value="DEPT_LEADER" />
            <el-option label="高管" value="EXECUTIVE" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="userForm.deptId" class="fill" placeholder="选择部门">
            <el-option
              v-for="dept in allDeptOptions"
              :key="dept.id"
              :label="dept.deptName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="saveUser">创建</el-button>
      </template>
    </el-dialog>

    <!-- 项目成员名单（穿透，不跳页） -->
    <MemberRosterDialog
      v-model="rosterVisible"
      :title="rosterTitle"
      :members="rosterMembers"
      :loading="rosterLoading"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Plus, Search } from '@element-plus/icons-vue'
import MemberRosterDialog from '@/components/common/MemberRosterDialog.vue'
import { createDept, getDeptMembers, getDeptTree, updateDept } from '@/api/dept'
import { createProject, getProjectMembers, listProjects } from '@/api/project'
import { createUser, getUsers, updateUserStatus } from '@/api/user'
import { getUser } from '@/utils/auth'

const router = useRouter()
const isAdmin = computed(() => getUser()?.role === 'ADMIN')

const tab = ref('dept')

/* ---------------- 部门 ---------------- */
const deptTree = ref([])
const deptLoading = ref(false)
const deptDialogVisible = ref(false)
const deptSaving = ref(false)
const deptForm = reactive({ id: null, parentId: 0, deptName: '', sortOrder: 0 })

const deptOptions = computed(() =>
  deptTree.value.flatMap((root) =>
    (root.children || []).map((child) => ({
      id: child.id,
      deptName: child.deptName,
      userCount: child.userCount
    }))
  )
)

async function loadDepts() {
  deptLoading.value = true
  try {
    const res = await getDeptTree()
    deptTree.value = res.data || []
    // 默认选中第一个部门，右侧直接看到成员
    const first = deptTree.value[0]?.children?.[0] || deptTree.value[0]
    if (first) {
      selectDept(first)
    }
  } catch {
    deptTree.value = []
  } finally {
    deptLoading.value = false
  }
}

/* ---------------- 部门成员（左树右名单） ---------------- */
const activeDept = ref(null)
const deptMembers = ref([])
const deptMemberLoading = ref(false)

async function selectDept(node) {
  if (!node) return
  activeDept.value = node
  deptMembers.value = []
  deptMemberLoading.value = true
  try {
    const res = await getDeptMembers(node.id)
    deptMembers.value = res.data || []
  } catch {
    deptMembers.value = []
  } finally {
    deptMemberLoading.value = false
  }
}

/* ---------------- 项目成员名单弹窗 ---------------- */
const rosterVisible = ref(false)
const rosterTitle = ref('成员名单')
const rosterMembers = ref([])
const rosterLoading = ref(false)

async function openProjectRoster(row) {
  rosterTitle.value = `${row.projectName} · 成员名单`
  rosterMembers.value = []
  rosterVisible.value = true
  rosterLoading.value = true
  try {
    const res = await getProjectMembers(row.id)
    rosterMembers.value = res.data || []
  } catch {
    rosterMembers.value = []
  } finally {
    rosterLoading.value = false
  }
}

function openCreateDept() {
  deptForm.id = null
  deptForm.parentId = deptTree.value[0]?.id ?? 0
  deptForm.deptName = ''
  deptForm.sortOrder = 0
  deptDialogVisible.value = true
}

function openEditDept(row) {
  deptForm.id = row.id
  deptForm.parentId = row.parentId ?? 0
  deptForm.deptName = row.deptName
  deptForm.sortOrder = row.sortOrder ?? 0
  deptDialogVisible.value = true
}

function resetDeptForm() {
  deptForm.id = null
  deptForm.deptName = ''
}

async function saveDept() {
  if (!deptForm.deptName?.trim()) {
    ElMessage.warning('请填写部门名称')
    return
  }
  deptSaving.value = true
  try {
    const payload = {
      parentId: deptForm.parentId,
      deptName: deptForm.deptName.trim(),
      sortOrder: deptForm.sortOrder
    }
    if (deptForm.id) {
      await updateDept(deptForm.id, payload)
      ElMessage.success('部门已更新')
    } else {
      await createDept(payload)
      ElMessage.success('部门已创建')
    }
    deptDialogVisible.value = false
    await loadDepts()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deptSaving.value = false
  }
}

/* ---------------- 用户 ---------------- */
const users = ref([])
const userTotal = ref(0)
const userLoading = ref(false)
const userQuery = reactive({ page: 1, size: 10, deptId: null, role: null, keyword: '' })

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await getUsers({
      page: userQuery.page,
      size: userQuery.size,
      deptId: userQuery.deptId || undefined,
      role: userQuery.role || undefined,
      keyword: userQuery.keyword || undefined
    })
    users.value = res.data?.records || []
    userTotal.value = res.data?.total || 0
  } catch {
    users.value = []
    userTotal.value = 0
  } finally {
    userLoading.value = false
  }
}

function searchUsers() {
  userQuery.page = 1
  loadUsers()
}

function onUserSizeChange() {
  userQuery.page = 1
  loadUsers()
}

/* 状态开关：先二次确认，确认后调接口并刷新 */
const statusUpdating = ref(null)

async function confirmToggleUser(row) {
  const action = row.status === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定${action}「${row.realName}」吗？`,
      `${action}账号`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }
    )
    return true
  } catch {
    return false
  }
}

async function applyToggleUser(row, enabled) {
  statusUpdating.value = row.id
  try {
    const res = await updateUserStatus(row.id, enabled ? 0 : 1)
    ElMessage.success(res.message || '操作成功')
    await loadUsers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    statusUpdating.value = null
  }
}

/* ---------------- 新增用户 ---------------- */
const userDialogVisible = ref(false)
const userSaving = ref(false)
const userForm = reactive({
  username: '',
  password: '',
  realName: '',
  role: 'EMPLOYEE',
  deptId: null
})

/** 用户可以选择公司本部或任意二级部门 */
const allDeptOptions = computed(() => {
  const list = []
  deptTree.value.forEach((root) => {
    list.push({ id: root.id, deptName: root.deptName })
    ;(root.children || []).forEach((child) => {
      list.push({ id: child.id, deptName: `${root.deptName} / ${child.deptName}` })
    })
  })
  return list
})

function openCreateUser() {
  userForm.username = ''
  userForm.password = ''
  userForm.realName = ''
  userForm.role = 'EMPLOYEE'
  userForm.deptId = allDeptOptions.value[1]?.id ?? allDeptOptions.value[0]?.id ?? null
  userDialogVisible.value = true
}

async function saveUser() {
  if (!userForm.username?.trim()) {
    ElMessage.warning('请填写登录账号')
    return
  }
  if (!userForm.password || userForm.password.length < 6) {
    ElMessage.warning('初始密码至少 6 位')
    return
  }
  if (!userForm.realName?.trim()) {
    ElMessage.warning('请填写真实姓名')
    return
  }
  if (!userForm.deptId) {
    ElMessage.warning('请选择所属部门')
    return
  }

  userSaving.value = true
  try {
    const res = await createUser({
      username: userForm.username.trim(),
      password: userForm.password,
      realName: userForm.realName.trim(),
      role: userForm.role,
      deptId: userForm.deptId
    })
    ElMessage.success(res.message || '用户已创建')
    userDialogVisible.value = false
    await loadUsers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    userSaving.value = false
  }
}

/* ---------------- 项目 ---------------- */
const projects = ref([])
const projectTotal = ref(0)
const projectLoading = ref(false)
const projectQuery = reactive({ page: 1, size: 10 })
const projectDialogVisible = ref(false)
const projectSaving = ref(false)
const projectForm = reactive({ projectName: '', leaderId: null, deptId: null, description: '' })
const leaderOptions = ref([])

async function loadProjects() {
  projectLoading.value = true
  try {
    const res = await listProjects({ page: projectQuery.page, size: projectQuery.size })
    projects.value = res.data?.records || []
    projectTotal.value = res.data?.total || 0
  } catch {
    projects.value = []
    projectTotal.value = 0
  } finally {
    projectLoading.value = false
  }
}

function onProjectSizeChange() {
  projectQuery.page = 1
  loadProjects()
}

async function loadLeaderOptions() {
  try {
    const res = await getUsers({ page: 1, size: 100 })
    leaderOptions.value = res.data?.records || []
  } catch {
    leaderOptions.value = []
  }
}

function openCreateProject() {
  projectForm.projectName = ''
  projectForm.leaderId = null
  projectForm.deptId = deptOptions.value[0]?.id ?? null
  projectForm.description = ''
  projectDialogVisible.value = true
}

async function saveProject() {
  if (!projectForm.projectName?.trim()) {
    ElMessage.warning('请填写项目名称')
    return
  }
  if (!projectForm.leaderId) {
    ElMessage.warning('请指定项目负责人')
    return
  }
  if (!projectForm.deptId) {
    ElMessage.warning('请选择所属部门')
    return
  }

  projectSaving.value = true
  try {
    await createProject({
      projectName: projectForm.projectName.trim(),
      leaderId: projectForm.leaderId,
      deptId: projectForm.deptId,
      description: projectForm.description?.trim() || undefined
    })
    ElMessage.success('项目已创建')
    projectDialogVisible.value = false
    await loadProjects()
  } catch {
    /* 拦截器已提示 */
  } finally {
    projectSaving.value = false
  }
}

onMounted(async () => {
  if (!isAdmin.value) return
  await loadDepts()
  await Promise.all([loadUsers(), loadProjects(), loadLeaderOptions()])
})
</script>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1240px;
  margin: 0 auto;
}

.settings__head {
  padding: 4px 2px 0;
}

.settings__head h1 {
  margin: 0;
  color: var(--wl-text);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 1px;
}

.settings__head p {
  margin: 8px 0 0;
  color: var(--wl-muted);
  font-size: 12px;
}

.card {
  padding: 8px 18px 12px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 14px 32px -26px rgba(63, 74, 90, 0.5);
}

.tabs :deep(.el-tabs__item) {
  font-size: 13px;
  color: rgba(40, 38, 36, 0.55);
}

.tabs :deep(.el-tabs__item.is-active) {
  color: var(--wl-primary);
  font-weight: 600;
}

.tabs :deep(.el-tabs__active-bar) {
  background: var(--wl-primary);
  height: 2px;
}

.tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(63, 74, 90, 0.06);
  height: 1px;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0;
  flex-wrap: wrap;
}

/* ---------------- 左树右名单 ---------------- */
.dept-split {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  margin-top: 4px;
}

.dept-tree {
  padding: 10px 8px;
  border-radius: 12px;
  background: #faf9f7;
  min-height: 320px;
}

.dept-tree :deep(.el-tree) {
  background: transparent;
}

.dept-tree :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 8px;
  transition: background-color 0.24s var(--wl-ease);
}

.dept-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(63, 74, 90, 0.08);
  color: var(--wl-primary);
  font-weight: 600;
}

.dept-members {
  border-radius: 12px;
  background: #fff;
  box-shadow:
    0 0 0 1px rgba(63, 74, 90, 0.05),
    0 12px 28px -26px rgba(63, 74, 90, 0.5);
  padding: 14px 16px;
  min-height: 320px;
}

.dept-members__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 6px;
  border-bottom: 1px solid rgba(63, 74, 90, 0.06);
}

.dept-members__head b {
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
}

.dept-members__count {
  padding: 2px 7px;
  border-radius: 6px;
  background: rgba(63, 74, 90, 0.06);
  color: rgba(40, 38, 36, 0.5);
  font-size: 11px;
}

.dept-members__edit {
  margin-left: auto;
}

.dept-hint {
  padding: 24px 0;
  margin: 0;
  color: rgba(40, 38, 36, 0.32);
  font-size: 12px;
  text-align: center;
}

.name-link {
  padding: 0;
  border: none;
  background: none;
  color: var(--wl-primary);
  font-size: 13px;
  cursor: pointer;
  border-bottom: 1px dashed rgba(63, 74, 90, 0.3);
  transition: color 0.24s var(--wl-ease);
}

.name-link:hover {
  color: var(--wl-violet);
}

.pane-head__hint {
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

.pane-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter {
  width: 140px;
}

.filter--kw {
  width: 180px;
}

/* 极简表格 */
.table {
  --el-table-border-color: transparent;
  --el-table-header-bg-color: #faf9f7;
  --el-table-row-hover-bg-color: rgba(63, 74, 90, 0.035);
  --el-table-header-text-color: rgba(40, 38, 36, 0.52);
  background: transparent;
}

.table :deep(th.el-table__cell) {
  border-bottom: 1px solid rgba(63, 74, 90, 0.07);
  font-size: 12px;
  font-weight: 600;
}

.table :deep(td.el-table__cell) {
  border-bottom: 1px solid rgba(63, 74, 90, 0.04);
  font-size: 13px;
  padding: 10px 0;
}

.table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.level {
  padding: 2px 8px;
  border-radius: 5px;
  background: rgba(63, 74, 90, 0.06);
  color: rgba(40, 38, 36, 0.55);
  font-size: 11px;
}

.status {
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
}

.status.is-on {
  background: rgba(63, 74, 90, 0.08);
  color: var(--wl-primary);
}

.status.is-off {
  background: rgba(185, 154, 122, 0.16);
  color: #8a6d4f;
}

.foot {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

.fill {
  width: 100%;
}

.denied {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 24px;
  border-radius: 16px;
  background: var(--wl-card);
  box-shadow: 0 0 0 1px rgba(63, 74, 90, 0.05);
  text-align: center;
}

.denied__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: rgba(122, 106, 147, 0.1);
  color: var(--wl-violet);
  font-size: 19px;
}

.denied h3 {
  margin: 6px 0 0;
  color: var(--wl-primary);
  font-size: 15px;
  font-weight: 600;
}

.denied p {
  margin: 0;
  color: rgba(40, 38, 36, 0.45);
  font-size: 12px;
}
</style>

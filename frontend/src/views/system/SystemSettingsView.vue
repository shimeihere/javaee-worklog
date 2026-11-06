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
            <el-table-column label="姓名" min-width="150">
              <template #default="{ row }">
                <span class="user-name">{{ row.realName }}</span>
                <span v-if="row.status === 1" class="status is-off">已禁用</span>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="账号" min-width="130" />
            <el-table-column prop="roleName" label="角色" width="130" />
            <el-table-column prop="deptName" label="部门" min-width="150" />
            <el-table-column label="参与项目" min-width="200">
              <template #default="{ row }">
                <span v-if="!row.projectNames?.length" class="muted">—</span>
                <span v-else class="proj-tags">
                  <el-tag
                    v-for="name in row.projectNames.slice(0, 2)"
                    :key="name"
                    size="small"
                    effect="plain"
                  >
                    {{ name }}
                  </el-tag>
                  <span v-if="row.projectNames.length > 2" class="proj-more">
                    +{{ row.projectNames.length - 2 }}
                  </span>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditUser(row)">编辑</el-button>
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
            <el-table-column label="周期" width="170">
              <template #default="{ row }">
                <span v-if="row.plannedEndDate" :class="{ 'cycle-overdue': row.overdue }">
                  第 {{ row.elapsedDays }} / {{ row.plannedDays }} 天
                  <template v-if="row.overdue">· 超期 {{ -row.remainingDays }} 天</template>
                  <template v-else>· 剩 {{ row.remainingDays }} 天</template>
                </span>
                <span v-else class="muted">已进行 {{ row.elapsedDays }} 天</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span class="status" :class="row.status === 0 ? 'is-on' : 'is-off'">
                  {{ row.status === 0 ? '进行中' : '已归档' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditProject(row)">编辑</el-button>
                <el-button link type="primary" @click="openLeaderDialog(row)">改负责人</el-button>
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

        <!-- ================= 审计日志 ================= -->
        <el-tab-pane label="审计日志" name="audit">
          <div class="pane-head">
            <div class="pane-filters">
              <el-select
                v-model="auditQuery.action"
                class="filter"
                placeholder="全部动作"
                clearable
                @change="searchAudit"
              >
                <el-option
                  v-for="item in auditActions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <el-input
                v-model="auditQuery.keyword"
                class="filter filter--kw"
                placeholder="操作人或明细"
                clearable
                :prefix-icon="Search"
                @keyup.enter="searchAudit"
              />
              <el-button @click="searchAudit">查询</el-button>
            </div>
            <span class="pane-head__hint">只记录管理动作，不包含日报周报正文</span>
          </div>

          <el-table v-loading="auditLoading" :data="auditLogs" class="table">
            <el-table-column prop="createTime" label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="110" />
            <el-table-column label="动作" width="140">
              <template #default="{ row }">
                <span class="status is-on">{{ row.actionName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="明细" min-width="380" show-overflow-tooltip />
          </el-table>

          <footer class="foot">
            <el-pagination
              v-model:current-page="auditQuery.page"
              v-model:page-size="auditQuery.size"
              :total="auditTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadAudit"
              @size-change="onAuditSizeChange"
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
        <el-form-item label="计划完成日期">
          <el-date-picker
            v-model="projectForm.plannedEndDate"
            type="date"
            value-format="YYYY-MM-DD"
            class="fill"
            placeholder="选填；填了才能算项目周期进度"
          />
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

    <!-- 新增用户弹窗：单个 / 批量 -->
    <el-dialog
      v-model="userDialogVisible"
      title="新增用户"
      width="580px"
      append-to-body
      @closed="resetUserDialog"
    >
      <el-tabs v-model="userDialogTab" class="inner-tabs">
        <el-tab-pane label="单个新增" name="single">
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
                <el-option
                  v-for="item in roleOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
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
        </el-tab-pane>

        <el-tab-pane label="批量新增" name="batch">
          <el-form label-position="top">
            <div class="batch-grid">
              <el-form-item label="统一初始密码" required>
                <el-input
                  v-model="batchForm.password"
                  type="password"
                  show-password
                  maxlength="32"
                  placeholder="6-32 位，本批共用"
                />
              </el-form-item>
              <el-form-item label="默认角色">
                <el-select v-model="batchForm.role" class="fill">
                  <el-option
                    v-for="item in roleOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="默认部门">
                <el-select v-model="batchForm.deptId" class="fill" placeholder="选择部门">
                  <el-option
                    v-for="dept in allDeptOptions"
                    :key="dept.id"
                    :label="dept.deptName"
                    :value="dept.id"
                  />
                </el-select>
              </el-form-item>
            </div>

            <el-form-item label="账号名单（每行一个）">
              <el-input
                v-model="batchForm.text"
                type="textarea"
                :rows="7"
                placeholder="wangwu,王五&#10;lisi,李四,部门负责人,技术中心&#10;zhaoliu,赵六,员工,产品设计部"
              />
            </el-form-item>
            <p class="batch-hint">
              每行：账号,姓名[,角色][,部门]，逗号 / Tab / 空格分隔；角色和部门留空就用上面的默认值。
              可以直接从 Excel 复制两列粘贴进来。
            </p>

            <div v-if="batchRows.length" class="batch-preview">
              <div class="batch-preview__head">
                预览 {{ batchRows.length }} 行
                <em v-if="batchInvalidCount">，其中 {{ batchInvalidCount }} 行需要修改</em>
                <span v-else>，全部可提交</span>
              </div>
              <ul>
                <li
                  v-for="(row, index) in batchRows"
                  :key="index"
                  :class="{ 'is-bad': row.error }"
                >
                  <span class="batch-preview__no">{{ index + 1 }}</span>
                  <b>{{ row.username || '—' }}</b>
                  <span class="batch-preview__name">{{ row.realName || '—' }}</span>
                  <span class="batch-preview__meta">{{ row.roleLabel }} · {{ row.deptLabel }}</span>
                  <em v-if="row.error">{{ row.error }}</em>
                </li>
              </ul>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button
          v-if="userDialogTab === 'single'"
          type="primary"
          :loading="userSaving"
          @click="saveUser"
        >
          创建
        </el-button>
        <el-button
          v-else
          type="primary"
          :loading="batchSaving"
          :disabled="!batchRows.length"
          @click="saveBatchUsers"
        >
          批量创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户：调部门 / 改角色 / 加项目 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="520px" append-to-body>
      <el-form :model="editForm" label-position="top">
        <el-form-item label="登录账号">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="真实姓名" required>
          <el-input v-model="editForm.realName" maxlength="50" placeholder="例如：张三" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="editForm.role" class="fill" :disabled="editForm.id === currentUserId">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <p v-if="editForm.id === currentUserId" class="batch-hint">
            不能修改自己的角色，避免把自己降权后系统没人管理。
          </p>
        </el-form-item>
        <el-form-item label="账号状态">
          <div class="status-row">
            <el-switch
              v-model="editForm.status"
              :active-value="0"
              :inactive-value="1"
              inline-prompt
              active-text="启用"
              inactive-text="禁用"
              :disabled="editForm.id === currentUserId"
            />
            <span class="status-row__hint">
              {{ editForm.status === 0 ? '可正常登录、填报日报周报' : '不能登录，历史日报与项目关系都保留' }}
            </span>
          </div>
        </el-form-item>
        <el-form-item label="所属部门" required>
          <el-select v-model="editForm.deptId" class="fill" placeholder="选择部门">
            <el-option
              v-for="dept in allDeptOptions"
              :key="dept.id"
              :label="dept.deptName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参与项目">
          <el-select
            v-model="editForm.projectIds"
            class="fill"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="不选表示不参与任何项目"
          >
            <el-option
              v-for="project in allProjects"
              :key="project.id"
              :label="project.projectName"
              :value="project.id"
            />
          </el-select>
          <p class="batch-hint">
            加到项目后，该员工填报日报时就能选到这个项目，项目成员名单里也会出现他。
          </p>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEditUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑项目：改名称 / 描述 / 归档 -->
    <el-dialog v-model="projectEditVisible" title="编辑项目" width="480px" append-to-body>
      <el-form :model="projectEditForm" label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="projectEditForm.projectName" maxlength="100" />
        </el-form-item>

        <el-form-item label="项目状态">
          <el-radio-group v-model="projectEditForm.status" class="seg">
            <el-radio-button :value="0">进行中</el-radio-button>
            <el-radio-button :value="1">已归档</el-radio-button>
          </el-radio-group>
          <p class="batch-hint">归档后仍保留历史日报与成员关系，只是不再出现在「进行中」的视角里。</p>
        </el-form-item>

        <el-form-item label="计划完成日期">
          <el-date-picker
            v-model="projectEditForm.plannedEndDate"
            type="date"
            value-format="YYYY-MM-DD"
            class="fill"
            placeholder="选填；填了才能算项目周期进度"
          />
        </el-form-item>

        <el-form-item label="项目描述">
          <el-input
            v-model="projectEditForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="一句话说明项目目标"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="projectEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="projectEditSaving" @click="saveProjectEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 变更项目负责人 -->
    <el-dialog
      v-model="leaderDialogVisible"
      title="变更项目负责人"
      width="420px"
      align-center
      class="roster-dialog"
      append-to-body
    >
      <p class="leader-hint">
        项目：<b>{{ leaderTarget?.projectName }}</b>
        <br />
        变更后会分别给新旧负责人各发一条系统通知。
      </p>

      <el-select v-model="leaderValue" class="fill" placeholder="选择新的负责人" filterable>
        <el-option
          v-for="item in leaderChoices"
          :key="item.id"
          :label="`${item.realName}（${item.deptName || '未分配'}）`"
          :value="item.id"
        />
      </el-select>

      <template #footer>
        <el-button @click="leaderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="leaderSaving" @click="saveLeader">确认变更</el-button>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock, Plus, Search } from '@element-plus/icons-vue'
import MemberRosterDialog from '@/components/common/MemberRosterDialog.vue'
import { createDept, getDeptMembers, getDeptTree, updateDept } from '@/api/dept'
import {
  changeProjectLeader,
  createProject,
  getProjectMembers,
  listProjects,
  updateProject
} from '@/api/project'
import { batchCreateUsers, createUser, getUsers, updateUser, updateUserStatus } from '@/api/user'
import { getAuditLogs } from '@/api/audit'
import { getUser } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const isAdmin = computed(() => getUser()?.role === 'ADMIN')

/** 支持 /system?tab=user|project|audit 直接落到对应页签（工作台快捷入口用） */
const TABS = ['dept', 'user', 'project', 'audit']
const tab = ref(TABS.includes(route.query.tab) ? route.query.tab : 'dept')

watch(
  () => route.query.tab,
  (value) => {
    if (TABS.includes(value)) {
      tab.value = value
    }
  }
)

const currentUserId = getUser()?.id ?? null
const roleOptions = [
  { value: 'EMPLOYEE', label: '员工' },
  { value: 'DEPT_LEADER', label: '部门负责人' },
  { value: 'EXECUTIVE', label: '高管' },
  { value: 'ADMIN', label: '管理员' }
]
const roleLabel = (code) => roleOptions.find((item) => item.value === code)?.label || '—'

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

/* ---------------- 变更项目负责人 ---------------- */
const leaderDialogVisible = ref(false)
const leaderTarget = ref(null)
const leaderValue = ref(null)
const leaderChoices = ref([])
const leaderSaving = ref(false)

/* ---------------- 编辑项目：名称 / 描述 / 归档 ---------------- */
const projectEditVisible = ref(false)
const projectEditSaving = ref(false)
const projectEditForm = reactive({
  id: null,
  projectName: '',
  description: '',
  status: 0,
  plannedEndDate: null
})

function openEditProject(row) {
  projectEditForm.id = row.id
  projectEditForm.projectName = row.projectName || ''
  projectEditForm.description = row.description || ''
  projectEditForm.status = row.status ?? 0
  projectEditForm.plannedEndDate = row.plannedEndDate || null
  projectEditVisible.value = true
}

async function saveProjectEdit() {
  if (!projectEditForm.projectName?.trim()) {
    ElMessage.warning('请填写项目名称')
    return
  }
  projectEditSaving.value = true
  try {
    await updateProject(projectEditForm.id, {
      projectName: projectEditForm.projectName.trim(),
      description: projectEditForm.description?.trim() || '',
      status: projectEditForm.status,
      plannedEndDate: projectEditForm.plannedEndDate || null
    })
    ElMessage.success('项目已更新')
    projectEditVisible.value = false
    await Promise.all([loadProjects(), loadProjectOptions()])
  } catch {
    /* 拦截器已提示 */
  } finally {
    projectEditSaving.value = false
  }
}

async function openLeaderDialog(row) {
  leaderTarget.value = row
  leaderValue.value = row.leaderId ?? null
  leaderDialogVisible.value = true
  if (leaderChoices.value.length) return
  try {
    const res = await getUsers({ page: 1, size: 100 })
    leaderChoices.value = res.data?.records || []
  } catch {
    leaderChoices.value = []
  }
}

async function saveLeader() {
  if (!leaderValue.value) {
    ElMessage.warning('请选择新的负责人')
    return
  }
  leaderSaving.value = true
  try {
    await changeProjectLeader(leaderTarget.value.id, leaderValue.value)
    ElMessage.success('负责人已变更，已通知新旧负责人')
    leaderDialogVisible.value = false
    loadProjects()
  } catch {
    /* 拦截器已提示 */
  } finally {
    leaderSaving.value = false
  }
}

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

/* ---------------- 用户：单个 / 批量新增 ---------------- */
const userDialogVisible = ref(false)
const userDialogTab = ref('single')
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

/** 批量导入时用短名匹配部门（粘贴的是"技术中心"，不是"周览科技 / 技术中心"） */
const deptNameMap = computed(() => {
  const map = new Map()
  deptTree.value.forEach((root) => {
    map.set(root.deptName, root.id)
    ;(root.children || []).forEach((child) => map.set(child.deptName, child.id))
  })
  return map
})

const deptLabelMap = computed(() => new Map(allDeptOptions.value.map((item) => [item.id, item.deptName])))

function defaultDeptId() {
  return allDeptOptions.value[1]?.id ?? allDeptOptions.value[0]?.id ?? null
}

function openCreateUser() {
  userForm.username = ''
  userForm.password = ''
  userForm.realName = ''
  userForm.role = 'EMPLOYEE'
  userForm.deptId = defaultDeptId()
  userDialogTab.value = 'single'
  resetBatchForm()
  userDialogVisible.value = true
}

function resetUserDialog() {
  resetBatchForm()
  userDialogTab.value = 'single'
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

/* ---------------- 批量新增：一行一个人，支持从 Excel 粘贴 ---------------- */
const ROLE_ALIAS = {
  EMPLOYEE: 'EMPLOYEE',
  DEPT_LEADER: 'DEPT_LEADER',
  EXECUTIVE: 'EXECUTIVE',
  ADMIN: 'ADMIN',
  员工: 'EMPLOYEE',
  负责人: 'DEPT_LEADER',
  部门负责人: 'DEPT_LEADER',
  高管: 'EXECUTIVE',
  管理员: 'ADMIN'
}

const batchSaving = ref(false)
const batchForm = reactive({ password: '', role: 'EMPLOYEE', deptId: null, text: '' })

function resetBatchForm() {
  batchForm.password = ''
  batchForm.role = 'EMPLOYEE'
  batchForm.deptId = defaultDeptId()
  batchForm.text = ''
}

/**
 * 解析粘贴的名单。
 * 每行：账号,姓名[,角色][,部门]，分隔符支持中英文逗号、Tab、空格；
 * 角色/部门没写的行，回落到弹窗顶部的默认值。
 */
const batchRows = computed(() => {
  const lines = (batchForm.text || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)

  return lines.map((line) => {
    const parts = line.split(/[\s,，、;；]+/).filter(Boolean)
    const row = {
      username: '',
      realName: '',
      role: batchForm.role,
      deptId: batchForm.deptId,
      error: ''
    }

    if (parts.length < 2) {
      row.username = parts[0] || ''
      row.error = '至少要有「账号 姓名」两列'
    } else {
      row.username = parts[0]
      row.realName = parts[1]
      parts.slice(2).forEach((token) => {
        const role = ROLE_ALIAS[token] || ROLE_ALIAS[token.toUpperCase()]
        if (role) {
          row.role = role
          return
        }
        const deptId = deptNameMap.value.get(token)
        if (deptId) {
          row.deptId = deptId
          return
        }
        row.error = row.error || `无法识别「${token}」`
      })
    }

    if (!row.error && !row.deptId) row.error = '没有可用的部门'
    if (!row.error && !row.role) row.error = '没有可用的角色'
    row.roleLabel = roleLabel(row.role)
    row.deptLabel = row.deptId ? deptLabelMap.value.get(row.deptId) || '—' : '—'
    return row
  })
})

const batchInvalidCount = computed(() => batchRows.value.filter((row) => row.error).length)

const escapeHtml = (text) =>
  String(text).replace(/[&<>"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[char])

async function saveBatchUsers() {
  if (!batchForm.password || batchForm.password.length < 6) {
    ElMessage.warning('统一初始密码至少 6 位')
    return
  }
  if (!batchRows.value.length) {
    ElMessage.warning('请先粘贴账号名单')
    return
  }
  if (batchInvalidCount.value > 0) {
    ElMessage.warning('预览里有 ' + batchInvalidCount.value + ' 行需要修改')
    return
  }

  batchSaving.value = true
  try {
    const res = await batchCreateUsers({
      password: batchForm.password,
      users: batchRows.value.map((row) => ({
        username: row.username,
        realName: row.realName,
        role: row.role,
        deptId: row.deptId
      }))
    })

    const data = res.data || {}
    if (data.failedCount) {
      await ElMessageBox.alert(
        `<div style="line-height:1.9;max-height:260px;overflow:auto">${
          (data.failures || []).map((item) => `<div>${escapeHtml(item)}</div>`).join('')
        }</div>`,
        `成功 ${data.createdCount} 个，失败 ${data.failedCount} 个`,
        { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
      )
    } else {
      ElMessage.success(res.message || `已批量创建 ${data.createdCount} 个用户`)
      userDialogVisible.value = false
    }
    await loadUsers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    batchSaving.value = false
  }
}

/* ---------------- 编辑用户：调部门 / 改角色 / 加项目 ---------------- */
const editDialogVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  id: null,
  username: '',
  realName: '',
  role: 'EMPLOYEE',
  deptId: null,
  projectIds: [],
  status: 0,
  /** 打开弹窗时的原状态，用来判断是否真的要调启用/禁用接口 */
  originStatus: 0
})

function openEditUser(row) {
  editForm.id = row.id
  editForm.username = row.username
  editForm.realName = row.realName || ''
  editForm.role = row.role || 'EMPLOYEE'
  editForm.deptId = row.deptId ?? null
  editForm.projectIds = [...(row.projectIds || [])]
  editForm.status = row.status ?? 0
  editForm.originStatus = editForm.status
  editDialogVisible.value = true
}

async function saveEditUser() {
  if (!editForm.realName?.trim()) {
    ElMessage.warning('请填写真实姓名')
    return
  }
  if (!editForm.deptId) {
    ElMessage.warning('请选择所属部门')
    return
  }

  editSaving.value = true
  try {
    await updateUser(editForm.id, {
      realName: editForm.realName.trim(),
      role: editForm.role,
      deptId: editForm.deptId,
      projectIds: editForm.projectIds
    })

    // 账号状态走原来的接口：保留"最后一个管理员不能禁用""有草稿会提醒"这些保护
    if (editForm.status !== editForm.originStatus) {
      const res = await updateUserStatus(editForm.id, editForm.status)
      ElMessage.success(res.message || '账号状态已更新')
    } else {
      ElMessage.success('用户已更新')
    }

    editDialogVisible.value = false
    await loadUsers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    editSaving.value = false
  }
}

/* ---------------- 审计日志 ---------------- */
const auditActions = [
  { value: 'CREATE_USER', label: '新增用户' },
  { value: 'BATCH_CREATE_USER', label: '批量新增用户' },
  { value: 'UPDATE_USER', label: '编辑用户' },
  { value: 'USER_STATUS', label: '启用/禁用账号' },
  { value: 'CREATE_DEPT', label: '新增部门' },
  { value: 'UPDATE_DEPT', label: '编辑部门' },
  { value: 'CREATE_PROJECT', label: '新建项目' },
  { value: 'UPDATE_PROJECT', label: '编辑项目' },
  { value: 'CHANGE_PROJECT_LEADER', label: '变更项目负责人' }
]

const auditLogs = ref([])
const auditTotal = ref(0)
const auditLoading = ref(false)
const auditQuery = reactive({ page: 1, size: 20, action: null, keyword: '' })

const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '—')

async function loadAudit() {
  auditLoading.value = true
  try {
    const res = await getAuditLogs({
      page: auditQuery.page,
      size: auditQuery.size,
      action: auditQuery.action || undefined,
      keyword: auditQuery.keyword || undefined
    })
    auditLogs.value = res.data?.records || []
    auditTotal.value = res.data?.total || 0
  } catch {
    auditLogs.value = []
    auditTotal.value = 0
  } finally {
    auditLoading.value = false
  }
}

function searchAudit() {
  auditQuery.page = 1
  loadAudit()
}

function onAuditSizeChange() {
  auditQuery.page = 1
  loadAudit()
}

/* ---------------- 项目 ---------------- */
const projects = ref([])
const projectTotal = ref(0)
const projectLoading = ref(false)
const projectQuery = reactive({ page: 1, size: 10 })
const projectDialogVisible = ref(false)
const projectSaving = ref(false)
const projectForm = reactive({
  projectName: '',
  leaderId: null,
  deptId: null,
  description: '',
  plannedEndDate: null
})
const leaderOptions = ref([])
/** 编辑用户时的项目下拉：不受分页限制，一次取 100 条 */
const allProjects = ref([])

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

async function loadProjectOptions() {
  try {
    const res = await listProjects({ page: 1, size: 100 })
    allProjects.value = res.data?.records || []
  } catch {
    allProjects.value = []
  }
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
  projectForm.plannedEndDate = null
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
      description: projectForm.description?.trim() || undefined,
      plannedEndDate: projectForm.plannedEndDate || null
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
  await Promise.all([loadUsers(), loadProjects(), loadLeaderOptions(), loadProjectOptions(), loadAudit()])
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

.leader-hint {
  margin: 0 0 14px;
  color: rgba(40, 38, 36, 0.55);
  font-size: 12px;
  line-height: 1.8;
}

.leader-hint b {
  color: var(--wl-text);
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

.cycle-overdue {
  color: #a2703f;
}

.foot {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 6px;
}

.fill {
  width: 100%;
}

.muted {
  color: rgba(40, 38, 36, 0.28);
  font-size: 12px;
}

.user-name {
  margin-right: 6px;
  color: var(--wl-text);
}

.status-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-row__hint {
  color: rgba(40, 38, 36, 0.45);
  font-size: 11px;
}

.seg :deep(.el-radio-button__inner) {
  border-color: rgba(63, 74, 90, 0.12);
  background: #fff;
  color: rgba(40, 38, 36, 0.6);
  font-size: 12px;
}

.seg :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--wl-primary);
  border-color: var(--wl-primary);
  color: #fff;
}

.proj-tags {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.proj-tags :deep(.el-tag) {
  --el-tag-border-color: rgba(63, 74, 90, 0.16);
  --el-tag-text-color: rgba(40, 38, 36, 0.62);
  --el-tag-bg-color: #faf9f7;
  font-size: 11px;
}

.proj-more {
  color: rgba(40, 38, 36, 0.36);
  font-size: 11px;
}

/* 弹窗内部的页面签 */
.inner-tabs :deep(.el-tabs__item) {
  font-size: 13px;
}

.batch-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 10px;
}

.batch-hint {
  margin: -2px 0 10px;
  color: rgba(40, 38, 36, 0.42);
  font-size: 11px;
  line-height: 1.7;
}

.batch-preview {
  border-radius: 10px;
  background: #faf9f7;
  padding: 8px 10px;
  max-height: 190px;
  overflow-y: auto;
}

.batch-preview__head {
  padding-bottom: 6px;
  color: rgba(40, 38, 36, 0.5);
  font-size: 11px;
}

.batch-preview__head em {
  color: #a2703f;
  font-style: normal;
}

.batch-preview ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.batch-preview li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-top: 1px solid rgba(63, 74, 90, 0.04);
  color: var(--wl-text);
  font-size: 12px;
}

.batch-preview li.is-bad {
  color: #a2703f;
}

.batch-preview__no {
  width: 18px;
  color: rgba(40, 38, 36, 0.3);
  font-size: 11px;
  text-align: right;
}

.batch-preview__name {
  color: rgba(40, 38, 36, 0.7);
}

.batch-preview__meta {
  margin-left: auto;
  color: rgba(40, 38, 36, 0.4);
  font-size: 11px;
}

.batch-preview li em {
  color: #a2703f;
  font-size: 11px;
  font-style: normal;
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
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import LocalReplanDialog from '../../components/v4/LocalReplanDialog.vue'
import { getSchedulePlanById, getSchedulePlanItems, type SchedulePlan, type SchedulePlanItem } from '../../api/schedulePlan'
import {
  getScheduleLockList,
  lockScheduleItem,
  unlockScheduleItem,
  type ScheduleLockItem,
  type ScheduleLockList,
} from '../../api/v4ScheduleLockApi'
import type { ScheduleReplanResult } from '../../api/v4ScheduleReplanApi'
import { strategyText } from '../../utils/status'

const route = useRoute()
const router = useRouter()

const planId = computed(() => Number(route.params.planId))
const loading = ref(false)
const actionLoadingKey = ref<string | null>(null)
const keyword = ref('')
const plan = ref<SchedulePlan | null>(null)
const planItems = ref<SchedulePlanItem[]>([])
const lockData = ref<ScheduleLockList | null>(null)
const localReplanVisible = ref(false)

const lockedPlanItemIds = computed(() => new Set((lockData.value?.items ?? []).map((item) => item.planItemId).filter(Boolean)))

const filteredLockedItems = computed(() => {
  const items = lockData.value?.items ?? []
  const search = keyword.value.trim().toLowerCase()
  if (!search) {
    return items
  }
  return items.filter((item) =>
    [item.courseName, item.teacherName, item.className, item.roomName, item.period, item.lockReason, item.createdAt]
      .join(' ')
      .toLowerCase()
      .includes(search),
  )
})

const filteredUnlockedItems = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return planItems.value.filter((item) => {
    if (lockedPlanItemIds.value.has(item.id)) {
      return false
    }
    if (!search) {
      return true
    }
    return [item.courseName, item.teacherName, item.className, item.roomName, item.timeLabel, item.conflictReason]
      .join(' ')
      .toLowerCase()
      .includes(search)
  })
})

async function fetchData() {
  loading.value = true
  try {
    const [planData, itemsData, lockList] = await Promise.all([
      getSchedulePlanById(planId.value),
      getSchedulePlanItems(planId.value),
      getScheduleLockList(planId.value),
    ])
    plan.value = planData
    planItems.value = itemsData
    lockData.value = lockList
  } catch (error: any) {
    ElMessage.error(error?.message || '加载锁定数据失败')
  } finally {
    loading.value = false
  }
}

async function refreshLocks() {
  lockData.value = await getScheduleLockList(planId.value)
}

async function handleLock(item: SchedulePlanItem) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请填写“${item.courseName || '课程'}”的锁定原因。后续局部重排会保留它当前的时间和教室。`,
      '锁定课程',
      {
        confirmButtonText: '确认锁定',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：该课程时间已人工确认，不参与后续重排',
        inputValidator: (input) => {
          if (!input || !input.trim()) {
            return '锁定原因不能为空'
          }
          return true
        },
      },
    )
    actionLoadingKey.value = `lock-${item.id}`
    await lockScheduleItem({
      targetType: 'PLAN',
      planId: planId.value,
      planItemId: item.id,
      lockReason: value,
    })
    await refreshLocks()
    ElMessage.success('课程已锁定')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '锁定失败')
    }
  } finally {
    actionLoadingKey.value = null
  }
}

async function handleUnlock(item: ScheduleLockItem) {
  try {
    await ElMessageBox.confirm(
      `确认取消“${item.courseName || '课程'}”的锁定吗？取消后它可以参与后续局部重排。`,
      '取消锁定',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '保留锁定',
      },
    )
    actionLoadingKey.value = `unlock-${item.lockId}`
    await unlockScheduleItem({
      targetType: item.targetType,
      planId: item.planId ?? planId.value,
      planItemId: item.planItemId ?? undefined,
      scheduleId: item.scheduleId ?? undefined,
    })
    await refreshLocks()
    ElMessage.success('课程已取消锁定')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '取消锁定失败')
    }
  } finally {
    actionLoadingKey.value = null
  }
}

function handleLocalReplanSuccess(result: ScheduleReplanResult) {
  localReplanVisible.value = false
  router.push(`/v3/schedule-plans/${result.newPlanId}`)
}

function formatPlanItemTime(item: SchedulePlanItem) {
  return `周${item.weekday} 第${item.startPeriod}-${item.endPeriod}节`
}

onMounted(fetchData)
</script>

<template>
  <div class="lock-page" v-loading="loading">
    <el-page-header content="V4 课程锁定管理" @back="router.push(`/v4/schedule-analysis/${planId}`)" />

    <el-card v-if="plan" shadow="never" class="header-card">
      <div class="header-top">
        <div>
          <div class="page-title">{{ plan.name }}</div>
          <div class="page-meta">{{ strategyText(plan.strategyType) }} · 学期 ID {{ plan.semesterId }} · 状态 {{ plan.status }}</div>
        </div>
        <div class="header-actions">
          <el-button @click="router.push(`/v3/schedule-plans/${plan.id}`)">回到 V3 方案详情</el-button>
          <el-button type="warning" plain @click="router.push(`/v4/schedule-analysis/${plan.id}`)">回到质量分析</el-button>
          <el-button
            type="success"
            plain
            :disabled="plan.status === 'ABANDONED' || plan.status === 'FAILED'"
            @click="localReplanVisible = true"
          >
            局部重排生成新方案
          </el-button>
        </div>
      </div>
      <div class="page-note">
        锁定只会标记“后续局部重排不要改这节课”，不会应用方案，也不会修改正式课表。
      </div>
    </el-card>

    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <div class="stat-box">
          <span>已锁定课程</span>
          <strong>{{ lockData?.lockedCount ?? 0 }}</strong>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-box">
          <span>可锁定课程</span>
          <strong>{{ filteredUnlockedItems.length }}</strong>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-box">
          <span>方案总课时项</span>
          <strong>{{ planItems.length }}</strong>
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never" class="toolbar-card">
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="搜索课程 / 教师 / 班级 / 教室 / 锁定原因" />
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-title">已锁定课程列表</div>
      </template>
      <el-table :data="filteredLockedItems" stripe>
        <el-table-column prop="courseName" label="课程" width="140" />
        <el-table-column prop="teacherName" label="教师" width="110" />
        <el-table-column prop="className" label="班级" width="140" />
        <el-table-column label="时间" width="130">
          <template #default="{ row }">周{{ row.weekDay }} 第{{ row.period }}节</template>
        </el-table-column>
        <el-table-column prop="roomName" label="教室" width="120" />
        <el-table-column prop="lockReason" label="锁定原因" min-width="240" />
        <el-table-column prop="createdAt" label="锁定时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              size="small"
              :loading="actionLoadingKey === `unlock-${row.lockId}`"
              @click="handleUnlock(row)"
            >
              取消锁定
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="filteredLockedItems.length === 0" description="当前还没有锁定课程" />
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-title">未锁定课程列表</div>
      </template>
      <el-table :data="filteredUnlockedItems" stripe>
        <el-table-column prop="courseName" label="课程" width="140" />
        <el-table-column prop="teacherName" label="教师" width="110" />
        <el-table-column prop="className" label="班级" width="140" />
        <el-table-column label="时间" width="130">
          <template #default="{ row }">{{ formatPlanItemTime(row) }}</template>
        </el-table-column>
        <el-table-column prop="roomName" label="教室" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.conflictFlag === 1 ? 'danger' : 'success'" size="small">
              {{ row.conflictFlag === 1 ? '有冲突' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conflictReason" label="冲突原因" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :loading="actionLoadingKey === `lock-${row.id}`"
              @click="handleLock(row)"
            >
              锁定课程
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="filteredUnlockedItems.length === 0" description="当前没有可锁定课程" />
    </el-card>
    <LocalReplanDialog v-model="localReplanVisible" :plan-id="planId" @success="handleLocalReplanSuccess" />
  </div>
</template>

<style scoped>
.lock-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-card,
.toolbar-card,
.table-card {
  border-radius: 18px;
}

.header-top,
.toolbar {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}

.page-meta,
.page-note {
  margin-top: 6px;
  color: #64748b;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stats-row {
  margin: 0;
}

.stat-box {
  min-height: 108px;
  border-radius: 20px;
  padding: 20px 22px;
  background: linear-gradient(135deg, #fff8ef 0%, #eef6ff 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-box span {
  color: #64748b;
}

.stat-box strong {
  font-size: 32px;
  color: #0f172a;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

@media (max-width: 768px) {
  .stats-row :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }
}
</style>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllSemesters, getCurrentSemester, type Semester } from '../../api/semester'
import { getSchedulePlanList, type SchedulePlan } from '../../api/schedulePlan'
import {
  getTeacherWorkload,
  getClassroomUtilization,
  getClassBalance,
  getPlanOverview,
  type TeacherWorkloadItem,
  type ClassroomUtilizationItem,
  type ClassBalanceItem,
  type PlanOverview,
} from '../../api/scheduleStatistics'
import { extractMessage } from '../../utils/errors'
import { fallback } from '../../utils/async'

const loading = ref(false)
const activeTab = ref('overview')

const semesterList = ref<Semester[]>([])
const currentSemester = ref<Semester | null>(null)
const planList = ref<SchedulePlan[]>([])

const searchForm = reactive({
  semesterId: undefined as number | undefined,
  planId: null as number | null,
})

const hasCurrentSemester = computed(() => currentSemester.value !== null)

const overview = ref<PlanOverview | null>(null)
const workloadData = ref<TeacherWorkloadItem[]>([])
const utilizationData = ref<ClassroomUtilizationItem[]>([])
const balanceData = ref<ClassBalanceItem[]>([])

async function fetchOptions() {
  const [semesters, current] = await Promise.all([
    fallback(getAllSemesters(), []),
    fallback(getCurrentSemester(), null),
  ])
  semesterList.value = semesters
  currentSemester.value = current
  if (current) {
    searchForm.semesterId = current.id
  }
}

async function fetchPlans() {
  if (!searchForm.semesterId) return
  try {
    const result = await getSchedulePlanList({ semesterId: searchForm.semesterId, page: 1, size: 100 })
    planList.value = result.records || []
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载方案列表失败'))
  }
}

async function fetchOverview() {
  loading.value = true
  try {
    const params: Record<string, number | undefined> = {}
    if (searchForm.semesterId) params.semesterId = searchForm.semesterId
    overview.value = await getPlanOverview(params)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载统计概览失败'))
  } finally {
    loading.value = false
  }
}

async function fetchWorkload() {
  loading.value = true
  try {
    const params: Record<string, number | undefined> = {}
    if (searchForm.semesterId) params.semesterId = searchForm.semesterId
    if (searchForm.planId) params.planId = searchForm.planId
    workloadData.value = await getTeacherWorkload(params)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载教师工作量失败'))
  } finally {
    loading.value = false
  }
}

async function fetchUtilization() {
  loading.value = true
  try {
    const params: Record<string, number | undefined> = {}
    if (searchForm.semesterId) params.semesterId = searchForm.semesterId
    if (searchForm.planId) params.planId = searchForm.planId
    utilizationData.value = await getClassroomUtilization(params)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载教室利用率失败'))
  } finally {
    loading.value = false
  }
}

async function fetchBalance() {
  loading.value = true
  try {
    const params: Record<string, number | undefined> = {}
    if (searchForm.semesterId) params.semesterId = searchForm.semesterId
    if (searchForm.planId) params.planId = searchForm.planId
    balanceData.value = await getClassBalance(params)
  } catch (error: unknown) {
    ElMessage.error(extractMessage(error, '加载班级均衡度失败'))
  } finally {
    loading.value = false
  }
}

function handleTabChange(tab: string) {
  if (tab === 'overview') fetchOverview()
  else if (tab === 'workload') fetchWorkload()
  else if (tab === 'utilization') fetchUtilization()
  else if (tab === 'balance') fetchBalance()
}

function onSemesterChange() {
  searchForm.planId = null
  fetchPlans()
  handleTabChange(activeTab.value)
}

function onPlanChange() {
  handleTabChange(activeTab.value)
}

function planName(planId: number | null | undefined) {
  if (!planId) return '正式课表'
  const plan = planList.value.find(p => p.id === planId)
  return plan ? plan.name : `方案${planId}`
}

function utilizationRateType(rate: number) {
  if (rate >= 80) return 'success'
  if (rate >= 50) return 'primary'
  if (rate >= 20) return 'warning'
  return 'danger'
}

function balanceScoreType(score: number) {
  if (score >= 0.8) return 'success'
  if (score >= 0.6) return 'primary'
  if (score >= 0.4) return 'warning'
  return 'danger'
}

function workloadEvalType(evaluation: string) {
  if (evaluation === '超负荷' || evaluation === '日课时偏高') return 'danger'
  if (evaluation === '正常偏多' || evaluation === '正常') return 'success'
  if (evaluation === '较轻') return 'primary'
  return 'info'
}

onMounted(async () => {
  await fetchOptions()
  await fetchPlans()
  await fetchOverview()
})
</script>

<template>
  <div class="page-container">
    <template v-if="!hasCurrentSemester">
      <el-alert title="当前未设置学期，请先创建或设置当前学期" type="warning" show-icon :closable="false" />
    </template>

    <template v-else>
      <!-- 筛选栏 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <el-alert :title="'当前学期：' + (currentSemester?.name || '—')" type="info" show-icon :closable="false" style="margin-bottom: 12px" />
        <el-row :gutter="16">
          <el-col :span="6">
            <el-select v-model="searchForm.semesterId" placeholder="选择学期" style="width: 100%" @change="onSemesterChange">
              <el-option v-for="s in semesterList" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-select v-model="searchForm.planId" placeholder="正式课表（默认）" clearable style="width: 100%" @change="onPlanChange">
              <el-option label="正式课表" :value="null" />
              <el-option v-for="p in planList" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-col>
          <el-col :span="12" style="display: flex; align-items: center; gap: 8px; color: var(--el-text-color-secondary); font-size: 13px">
            <span>{{ planName(searchForm.planId) }}</span>
          </el-col>
        </el-row>
      </el-card>

      <!-- 统计 Tabs -->
      <el-card shadow="never" v-loading="loading">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">

          <!-- 方案总览 Tab -->
          <el-tab-pane label="方案总览" name="overview">
            <el-row :gutter="16" style="margin-bottom: 16px">
              <el-col :span="4">
                <el-statistic title="方案总数" :value="overview?.totalPlans ?? 0" />
              </el-col>
              <el-col :span="4">
                <el-statistic title="草稿方案" :value="overview?.draftPlans ?? 0" />
              </el-col>
              <el-col :span="4">
                <el-statistic title="已应用方案" :value="overview?.appliedPlans ?? 0" />
              </el-col>
              <el-col :span="4">
                <el-statistic title="已废弃方案" :value="overview?.abandonedPlans ?? 0" />
              </el-col>
              <el-col :span="4">
                <el-statistic title="正式课表课程" :value="overview?.formalScheduleCount ?? 0" />
              </el-col>
              <el-col :span="4">
                <el-statistic title="未排任务合计" :value="overview?.totalUnassignedTasks ?? 0" />
              </el-col>
            </el-row>

            <el-divider />

            <el-row :gutter="16" v-if="overview" style="margin-bottom: 16px">
              <el-col :span="12">
                <el-descriptions title="最优方案" :column="2" border>
                  <el-descriptions-item label="方案名称">{{ overview.bestPlanName || '—' }}</el-descriptions-item>
                  <el-descriptions-item label="评分">{{ overview.bestPlanScore ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="策略">{{ overview.bestPlanStrategy || '—' }}</el-descriptions-item>
                </el-descriptions>
              </el-col>
              <el-col :span="12">
                <el-descriptions title="当前已应用方案" :column="2" border>
                  <el-descriptions-item label="方案名称">{{ overview.hasAppliedPlan ? (overview.appliedPlanName || '—') : '暂无' }}</el-descriptions-item>
                  <el-descriptions-item label="评分">{{ overview.appliedPlanScore ?? '—' }}</el-descriptions-item>
                  <el-descriptions-item label="应用时间">{{ overview.appliedPlanAppliedAt || '—' }}</el-descriptions-item>
                </el-descriptions>
              </el-col>
            </el-row>

            <el-empty v-if="!overview" description="暂无数据" />
          </el-tab-pane>

          <!-- 教师工作量 Tab -->
          <el-tab-pane label="教师工作量" name="workload">
            <div class="summary-note">统计每位教师的周课时总数、每日最大课时、最长连续大节、课程数和班级数。</div>
            <el-table :data="workloadData" stripe size="small">
              <el-table-column prop="teacherName" label="教师" width="120" />
              <el-table-column prop="department" label="院系" width="120" />
              <el-table-column label="周总课时" width="100" align="center">
                <template #default="{ row }"><span>{{ row.totalPeriods }}</span> 节</template>
              </el-table-column>
              <el-table-column label="日最大课时" width="100" align="center">
                <template #default="{ row }"><span>{{ row.maxDailyPeriods }}</span> 节</template>
              </el-table-column>
              <el-table-column label="最长连续" width="100" align="center">
                <template #default="{ row }"><span>{{ row.maxContinuousPeriods }}</span> 大节</template>
              </el-table-column>
              <el-table-column prop="courseCount" label="课程数" width="80" align="center" />
              <el-table-column prop="classCount" label="班级数" width="80" align="center" />
              <el-table-column label="周一至周五" min-width="200">
                <template #default="{ row }">
                  <span v-for="day in [1,2,3,4,5]" :key="day" style="margin-right: 8px">
                    周{{ day }}: <span>{{ row.dailyPeriods?.[day] || 0 }}</span> 节
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="评价" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="workloadEvalType(row.evaluation)" size="small">{{ row.evaluation }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="workloadData.length === 0" description="暂无数据" />
          </el-tab-pane>

          <!-- 教室利用率 Tab -->
          <el-tab-pane label="教室利用率" name="utilization">
            <div class="summary-note">统计每个教室的利用率 = 已用节次 / 总可用节次（20节/周）。</div>
            <el-table :data="utilizationData" stripe size="small">
              <el-table-column prop="roomName" label="教室" width="120" />
              <el-table-column prop="building" label="楼宇" width="100" />
              <el-table-column prop="capacity" label="容量" width="80" align="center" />
              <el-table-column label="类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.roomType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="已用节次" width="100" align="center">
                <template #default="{ row }"><span>{{ row.usedPeriods }}</span> 节</template>
              </el-table-column>
              <el-table-column label="利用率" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="utilizationRateType(row.utilizationRate)" size="small"><span>{{ row.utilizationRate }}</span>%</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="评价" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="utilizationRateType(row.utilizationRate)" size="small">{{ row.evaluation }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="utilizationData.length === 0" description="暂无数据" />
          </el-tab-pane>

          <!-- 班级均衡度 Tab -->
          <el-tab-pane label="班级均衡度" name="balance">
            <div class="summary-note">统计每个班级周一至周五的课时分布均衡程度，均衡分越接近 1 越均衡。</div>
            <el-table :data="balanceData" stripe size="small">
              <el-table-column prop="className" label="班级" width="150" />
              <el-table-column prop="studentCount" label="人数" width="80" align="center" />
              <el-table-column label="周一" width="80" align="center">
                <template #default="{ row }"><span>{{ row.day1Periods || 0 }}</span> 节</template>
              </el-table-column>
              <el-table-column label="周二" width="80" align="center">
                <template #default="{ row }"><span>{{ row.day2Periods || 0 }}</span> 节</template>
              </el-table-column>
              <el-table-column label="周三" width="80" align="center">
                <template #default="{ row }"><span>{{ row.day3Periods || 0 }}</span> 节</template>
              </el-table-column>
              <el-table-column label="周四" width="80" align="center">
                <template #default="{ row }"><span>{{ row.day4Periods || 0 }}</span> 节</template>
              </el-table-column>
              <el-table-column label="周五" width="80" align="center">
                <template #default="{ row }"><span>{{ row.day5Periods || 0 }}</span> 节</template>
              </el-table-column>
              <el-table-column label="总课时" width="90" align="center">
                <template #default="{ row }"><span>{{ row.totalPeriods }}</span> 节</template>
              </el-table-column>
              <el-table-column label="均衡分" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="balanceScoreType(row.balanceScore)" size="small">{{ row.balanceScore }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="评价" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="balanceScoreType(row.balanceScore)" size="small">{{ row.evaluation }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="balanceData.length === 0" description="暂无数据" />
          </el-tab-pane>

        </el-tabs>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
}

.summary-note {
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

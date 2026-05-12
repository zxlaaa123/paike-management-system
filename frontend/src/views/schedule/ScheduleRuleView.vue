<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getScheduleRules, updateScheduleRules, resetScheduleRules, type ScheduleRuleConfig } from '../../api/scheduleRule'

const loading = ref(false)
const saving = ref(false)
const rules = ref<ScheduleRuleConfig[]>([])

const form = reactive({
  teacherMaxDailySlots: 3,
  classMaxDailySlots: 4,
  prioritizeMorning: true,
  avoidFridayAfternoon: true,
  allowSameCourseSameDay: false,
})

async function fetchRules() {
  loading.value = true
  try {
    rules.value = await getScheduleRules()
    for (const rule of rules.value) {
      switch (rule.ruleKey) {
        case 'TEACHER_MAX_DAILY_SLOTS':
          form.teacherMaxDailySlots = parseInt(rule.ruleValue) || 3
          break
        case 'CLASS_MAX_DAILY_SLOTS':
          form.classMaxDailySlots = parseInt(rule.ruleValue) || 4
          break
        case 'PRIORITIZE_MORNING':
          form.prioritizeMorning = rule.ruleValue === 'true'
          break
        case 'AVOID_FRIDAY_AFTERNOON':
          form.avoidFridayAfternoon = rule.ruleValue === 'true'
          break
        case 'ALLOW_SAME_COURSE_SAME_DAY':
          form.allowSameCourseSameDay = rule.ruleValue === 'true'
          break
      }
    }
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (form.teacherMaxDailySlots <= 0) {
    ElMessage.error('教师每天最大课程数必须大于 0')
    return
  }
  if (form.classMaxDailySlots <= 0) {
    ElMessage.error('班级每天最大课程数必须大于 0')
    return
  }
  saving.value = true
  try {
    const payload = [
      { ruleKey: 'TEACHER_MAX_DAILY_SLOTS', ruleValue: String(form.teacherMaxDailySlots), enabled: 1 },
      { ruleKey: 'CLASS_MAX_DAILY_SLOTS', ruleValue: String(form.classMaxDailySlots), enabled: 1 },
      { ruleKey: 'PRIORITIZE_MORNING', ruleValue: String(form.prioritizeMorning), enabled: 1 },
      { ruleKey: 'AVOID_FRIDAY_AFTERNOON', ruleValue: String(form.avoidFridayAfternoon), enabled: 1 },
      { ruleKey: 'ALLOW_SAME_COURSE_SAME_DAY', ruleValue: String(form.allowSameCourseSameDay), enabled: 1 },
    ]
    await updateScheduleRules(payload)
    ElMessage.success('保存成功')
    fetchRules()
  } catch (_e) {
    // 错误由拦截器处理
  } finally {
    saving.value = false
  }
}

async function handleReset() {
  await ElMessageBox.confirm('确定恢复默认配置吗？当前配置将被覆盖。', '提示', { type: 'warning' })
  try {
    await resetScheduleRules()
    ElMessage.success('已恢复默认配置')
    fetchRules()
  } catch (_e) {
    // 错误由拦截器处理
  }
}

onMounted(fetchRules)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>排课规则配置</span>
          <div>
            <el-button @click="handleReset">恢复默认配置</el-button>
            <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="220px" class="rule-form">
        <el-form-item label="教师每天最多课程大节数">
          <el-input-number v-model="form.teacherMaxDailySlots" :min="1" :max="10" />
          <span class="form-tip">每位教师每天最多安排的大节数量</span>
        </el-form-item>

        <el-form-item label="班级每天最多课程大节数">
          <el-input-number v-model="form.classMaxDailySlots" :min="1" :max="10" />
          <span class="form-tip">每个班级每天最多安排的大节数量</span>
        </el-form-item>

        <el-divider />

        <el-form-item label="优先上午排课">
          <el-switch v-model="form.prioritizeMorning" />
          <span class="form-tip">自动排课时优先安排上午时间段</span>
        </el-form-item>

        <el-form-item label="避免周五下午排课">
          <el-switch v-model="form.avoidFridayAfternoon" />
          <span class="form-tip">自动排课时尽量避免安排周五下午课程</span>
        </el-form-item>

        <el-form-item label="允许同一课程同一天重复出现">
          <el-switch v-model="form.allowSameCourseSameDay" />
          <span class="form-tip">同一班级同一课程是否可以在一天内排多次</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  max-width: 700px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rule-form {
  padding: 16px 0;
}
.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
</style>

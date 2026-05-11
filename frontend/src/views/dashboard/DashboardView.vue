<template>
  <div class="dashboard-page">
    <el-card shadow="never">
      <template #header>
        <div class="header">首页统计（占位页）</div>
      </template>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="阶段 2 仅搭建基础框架，统计数据将在后续阶段接入。"
      />
      <div class="cards">
        <el-card v-for="item in placeholders" :key="item.label" shadow="hover">
          <div class="label">{{ item.label }}</div>
          <div class="value">--</div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
const placeholders = [
  { label: '教师数量' },
  { label: '班级数量' },
  { label: '教室数量' },
  { label: '课程数量' },
]
</script>

<style scoped>
.dashboard-page {
  padding: 8px;
}

.header {
  font-weight: 600;
}

.cards {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 1fr));
  gap: 12px;
}

.label {
  color: #909399;
  font-size: 14px;
}

.value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 600;
}
</style>

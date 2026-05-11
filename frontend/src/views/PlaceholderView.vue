<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const moduleLabel = computed(() => String(route.params.module || 'module'))
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div>页面占位</div>
    </template>
    <el-empty :description="`${moduleLabel} 页面将在后续阶段实现`" />
  </el-card>
</template>

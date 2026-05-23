<template>
  <ErrorBoundary>
    <RouterView />
  </ErrorBoundary>
</template>

<script setup lang="ts">
import ErrorBoundary from './components/ErrorBoundary.vue'
</script>

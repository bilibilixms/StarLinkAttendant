<script setup lang="ts">
defineProps<{
  data: any[]
  loading?: boolean
  stripe?: boolean
  border?: boolean
}>()
</script>

<template>
  <el-table
    :data="data"
    :loading="loading"
    :stripe="stripe ?? true"
    :border="border ?? false"
    style="width: 100%"
    v-loading="loading"
  >
    <slot />
  </el-table>
</template>

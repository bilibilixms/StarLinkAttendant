<script setup lang="ts">
defineProps<{
  loading?: boolean
}>()

const emit = defineEmits<{
  search: []
  reset: []
}>()
</script>

<template>
  <div class="search-form">
    <el-form :inline="true" @submit.prevent>
      <slot />
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="emit('search')">
          搜索
        </el-button>
        <el-button @click="emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.search-form {
  margin-bottom: 16px;
}
</style>

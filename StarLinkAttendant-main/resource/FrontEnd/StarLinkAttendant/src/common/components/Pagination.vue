<script setup lang="ts">
defineProps<{
  total: number
  page: number
  size: number
}>()

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
}>()
</script>

<template>
  <div class="pagination-wrapper">
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @update:current-page="emit('update:page', $event)"
      @update:page-size="emit('update:size', $event)"
    />
  </div>
</template>

<style scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 0;
}
</style>

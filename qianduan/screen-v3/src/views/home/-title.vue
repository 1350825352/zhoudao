<template>
  <div class="view-title">
    <div class="title-left"></div>
    <div class="title-text">{{ title }}</div>
    <div class="title-right"></div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<style lang="less" scoped>
.view-title {
  display: flex;
  align-items: center;
  height: 44px !important;
  flex-shrink: 0;
  min-height: 44px !important;
  padding: 0 16px;
  margin-bottom: 12px;
  background: linear-gradient(90deg, rgba(46, 139, 255, 0.2) 0%, rgba(46, 139, 255, 0.05) 50%, transparent 100%);
  border-left: 3px solid #2e8bff;
  box-sizing: border-box;

  .title-left,
  .title-right {
    flex: 1;
    height: 1px;
  }
  .title-left {
    max-width: 20px;
    margin-right: 12px;
    background: linear-gradient(90deg, transparent, #2e8bff);
  }
  .title-right {
    margin-left: 12px;
    background: linear-gradient(90deg, #2e8bff, transparent);
  }
  .title-text {
    font-size: 16px;
    font-weight: 600;
    color: #ffffff;
    letter-spacing: 2px;
    text-shadow: 0 0 10px rgba(46, 139, 255, 0.6);
  }
}
</style>

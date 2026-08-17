<template>
  <div class="screen-footer">
    <div class="footer-inner">
      <div
        v-for="(item, index) in menuList"
        :key="index"
        class="footer-block"
        @click="handleClick(item)"
      >
        <div class="block-icon">{{ item.icon }}</div>
        <div class="block-text">{{ item.name }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits(['live', 'plan', 'sikong', 'shibie', 'jumpOrder', 'playback'])

const menuList = [
  { name: '实时直播', icon: '📡', type: 'live' },
  { name: '航线规划', icon: '✈️', type: 'plan' },
  { name: '司空2', icon: '🗺️', type: 'sikong' },
  { name: 'AI识别', icon: '🤖', type: 'shibie' },
  { name: '工单管理', icon: '📋', type: 'jumpOrder' },
  { name: '视频回放', icon: '🎬', type: 'playback' }
]

const handleClick = (item: any) => {
  emit(item.type)
}
</script>

<style lang="less" scoped>
.screen-footer {
  width: 100%;
  height: 130px;
  padding: 20px 60px;
  background: linear-gradient(0deg, rgba(10, 26, 74, 0.95) 0%, rgba(5, 15, 44, 0.8) 100%);
  border-top: 1px solid rgba(46, 139, 255, 0.3);
  box-shadow: 0 -2px 20px rgba(46, 139, 255, 0.15);

  .footer-inner {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-around;
    gap: 30px;
  }

  .footer-block {
    flex: 1;
    height: 90px;
    max-width: 200px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    cursor: pointer;
    background: linear-gradient(135deg, rgba(46, 139, 255, 0.15), rgba(46, 139, 255, 0.02));
    border: 1px solid rgba(46, 139, 255, 0.5);
    border-radius: 8px;
    transition: all 0.3s ease;
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      top: -50%;
      left: -50%;
      width: 200%;
      height: 200%;
      background: conic-gradient(from 0deg, transparent, rgba(46, 139, 255, 0.3), transparent 30%);
      animation: rotate 4s linear infinite;
      opacity: 0;
      transition: opacity 0.3s;
    }

    &::after {
      content: '';
      position: absolute;
      inset: 1px;
      background: linear-gradient(135deg, rgba(5, 15, 44, 0.95), rgba(10, 26, 74, 0.95));
      border-radius: 7px;
      z-index: 0;
    }

    &:hover {
      transform: translateY(-3px);
      border-color: #2e8bff;
      box-shadow:
        0 0 20px rgba(46, 139, 255, 0.6),
        0 0 40px rgba(46, 139, 255, 0.3),
        inset 0 0 20px rgba(46, 139, 255, 0.1);

      &::before {
        opacity: 1;
      }
    }

    .block-icon {
      position: relative;
      z-index: 1;
      font-size: 30px;
      filter: drop-shadow(0 0 8px rgba(46, 139, 255, 0.8));
    }

    .block-text {
      position: relative;
      z-index: 1;
      font-size: 15px;
      font-weight: 600;
      color: #ffffff;
      letter-spacing: 2px;
      text-shadow: 0 0 8px rgba(46, 139, 255, 0.6);
    }
  }
}

@keyframes rotate {
  100% {
    transform: rotate(360deg);
  }
}
</style>

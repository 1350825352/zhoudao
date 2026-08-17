<template>
  <div class="device-row">
    <view-title title="设备动态" />

    <div class="plane-status-single" v-if="selectedUav">
      <span class="plane-sn">{{ selectedUav.name }}</span>
    </div>

    <div class="bottom">
      <div class="tray-bg"></div>
      <div class="drone-img"></div>
      <div class="bt">设备动态</div>
      <div class="planet">
        <div class="ball">
          <div class="num">{{ currentUavStatus?.satelliteCount || '--' }}</div>
          <div class="text">卫星数</div>
        </div>
        <div class="ball">
          <div class="num">{{ currentUavStatus?.battery || '--' }}%</div>
          <div class="text">电量</div>
        </div>
        <div class="ball">
          <div class="num">{{ currentUavStatus?.height?.toFixed(1) || '--' }}</div>
          <div class="text">高度(M)</div>
        </div>
        <div class="ball">
          <div class="num">{{ currentUavStatus?.speed?.toFixed(1) || '--' }}</div>
          <div class="text">速度(M/S)</div>
        </div>
        <div class="ball">
          <div class="num">{{ currentUavStatus?.distanceToHome?.toFixed(1) || '--' }}</div>
          <div class="text">距Home(M)</div>
        </div>
        <div class="ball">
          <div class="num">{{ currentUavStatus?.elevation?.toFixed(1) || '--' }}</div>
          <div class="text">相对起飞点高度(M)</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch, ref } from 'vue'
import viewTitle from './-title.vue'
import { useAppStore } from '@/store'
import { deviceApi } from '@/api/uav'

const store = useAppStore()

const selectedUav = computed(() => {
  if (!store.selectedSn) return null
  const apiStatus = store.uavStatusMap[store.selectedSn]
  if (apiStatus) {
    return {
      sn: apiStatus.sn,
      name: apiStatus.name,
      status: apiStatus.status,
      mode: apiStatus.mode as UavMode
    }
  }
  const device = store.devices.find(d => d.sn === store.selectedSn)
  if (device && device.kind === 'uav') {
    return {
      sn: device.sn,
      name: device.name,
      status: device.status,
      mode: undefined
    }
  }
  return null
})

const currentUavStatus = computed(() => {
  if (!store.selectedSn) return null
  return store.uavStatusMap[store.selectedSn]
})

const loadUavStatus = async (sn: string) => {
  try {
    const res = await deviceApi.getUavStatus(sn)
    store.updateUavStatus(sn, res.data)
  } catch (e) {
    console.warn('获取飞机状态失败', e)
  }
}

let refreshTimer: any = null

watch(() => store.selectedSn, (newSn) => {
  if (newSn) {
    loadUavStatus(newSn)
    if (refreshTimer) clearInterval(refreshTimer)
    refreshTimer = setInterval(() => loadUavStatus(newSn), 2000)
  }
})

onMounted(() => {
  if (store.selectedSn) {
    loadUavStatus(store.selectedSn)
    refreshTimer = setInterval(() => loadUavStatus(store.selectedSn), 2000)
  }
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="less" scoped>
.device-row {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;

  .plane-status-single {
    flex-shrink: 0;
    margin: 0 14px 12px;
    padding: 10px 16px;
    display: flex;
    align-items: center;
    gap: 10px;
    background: linear-gradient(90deg, rgba(46,139,255,0.18), rgba(46,139,255,0.05));
    border: 1px solid rgba(46,139,255,0.5);
    border-radius: 8px;
    box-shadow: 0 0 14px rgba(46,139,255,0.2), inset 0 0 18px rgba(46,139,255,0.06);

    .plane-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      display: inline-block;
      flex-shrink: 0;
      &.flying { background: #2e8bff; box-shadow: 0 0 8px rgba(46,139,255,0.95); animation: blink 1.2s infinite; }
      &.online { background: #52c41a; box-shadow: 0 0 8px rgba(82,196,26,0.9); }
      &.offline { background: #8c8c8c; box-shadow: none; }
    }
    .plane-sn {
      font-size: 16px;
      font-weight: 800;
      color: #ffffff;
      letter-spacing: 1px;
      text-shadow: 0 0 8px rgba(46,139,255,0.6);
    }
    .plane-sep {
      color: #5ca8ff;
      font-weight: 700;
      font-size: 14px;
    }
    .plane-status-text {
      font-size: 13px;
      color: #8ec4ff;
      font-weight: 600;
      letter-spacing: 0.5px;
    }
    .mode-tag {
      margin-left: auto;
      display: flex;
      align-items: center;
      gap: 5px;
      padding: 4px 12px;
      border-radius: 12px;
      border: 1px solid;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.4px;
      .mode-icon { font-size: 14px; }
      .mode-name { color: #fff; white-space: nowrap; }

      &.mode-auto { background: rgba(46,139,255,0.2); border-color: rgba(46,139,255,0.7); color: #5ca8ff; box-shadow: 0 0 10px rgba(46,139,255,0.35); }
      &.mode-guided { background: rgba(155,89,255,0.2); border-color: rgba(155,89,255,0.7); color: #b48cff; box-shadow: 0 0 10px rgba(155,89,255,0.35); }
      &.mode-rtl { background: rgba(250,173,20,0.2); border-color: rgba(250,173,20,0.7); color: #ffc53d; box-shadow: 0 0 10px rgba(250,173,20,0.35); }
      &.mode-land { background: rgba(255,77,79,0.2); border-color: rgba(255,77,79,0.7); color: #ff7875; box-shadow: 0 0 10px rgba(255,77,79,0.35); }
      &.mode-takeoff { background: rgba(82,196,26,0.2); border-color: rgba(82,196,26,0.7); color: #73d13d; box-shadow: 0 0 10px rgba(82,196,26,0.35); }
      &.mode-loiter { background: rgba(19,194,194,0.2); border-color: rgba(19,194,194,0.7); color: #36cfc9; box-shadow: 0 0 10px rgba(19,194,194,0.35); }
      &.mode-standby { background: rgba(140,140,140,0.2); border-color: rgba(140,140,140,0.55); color: #bfbfbf; box-shadow: none; }
      &.mode-manual { background: rgba(255,158,43,0.2); border-color: rgba(255,158,43,0.7); color: #ffa940; box-shadow: 0 0 10px rgba(255,158,43,0.35); }
    }
  }

  @keyframes blink {
    0%,100% { opacity: 1; }
    50% { opacity: 0.3; }
  }

  .bottom {
    flex: 1;
    width: 400px;
    min-height: 0;
    position: relative;
    left: 50%;
    transform: translateX(-50%);
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: auto 0;
    height: 258px;

    .tray-bg {
      position: absolute;
      left: 0;
      top: 0;
      width: 100%;
      height: 100%;
      background-image: url('./img/deviceCenter.png');
      background-size: 100% 100%;
      background-repeat: no-repeat;
      background-position: center;
      z-index: 0;
      -webkit-mask-image: linear-gradient(to bottom, transparent 0%, transparent 55%, rgba(0,0,0,0.2) 70%, rgba(0,0,0,0.85) 82%, #000 90%, #000 100%);
              mask-image: linear-gradient(to bottom, transparent 0%, transparent 55%, rgba(0,0,0,0.2) 70%, rgba(0,0,0,0.85) 82%, #000 90%, #000 100%);
      pointer-events: none;
    }

    .drone-img {
      position: absolute;
      left: 50%;
      top: 50%;
      width: 200px;
      height: 200px;
      margin: -85px 0 0 -100px;
      background-image: url('./img/plane.png');
      background-size: contain;
      background-repeat: no-repeat;
      background-position: center;
      z-index: 2;
      filter: drop-shadow(0 0 14px rgba(46, 139, 255, 0.85)) drop-shadow(0 0 28px rgba(46, 139, 255, 0.45));
      animation: drone-float 3s ease-in-out infinite;
    }

    .bt {
      position: absolute;
      left: 50%;
      top: 0;
      width: 120px;
      margin-left: -60px;
      text-align: center;
      font-family: MicrosoftYaHei, MicrosoftYaHei;
      font-weight: bold;
      font-size: 20px;
      background: linear-gradient(to bottom, #eeeeee 0%, #a6e1fe 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      z-index: 5;
      letter-spacing: 2px;
    }

    .planet {
      transform: scaleY(0.5) rotateZ(45deg);
      position: absolute;
      left: 50%;
      top: 50%;
      margin: -130px 0 0 -130px;
      transform-style: preserve-3d;
      width: 260px;
      height: 260px;
      background-image: url('./img/ellipse.png');
      background-size: 100% 100%;
      background-repeat: no-repeat;
      filter: drop-shadow(0 0 14px rgba(46, 139, 255, 0.45));
      z-index: 3;
    }

    .ball {
      width: 35px;
      height: 35px;
      background-image: url('./img/deviceMemo.png');
      background-size: 100% 100%;
      background-repeat: no-repeat;
      text-align: center;
      line-height: 35px;
      color: #f9f9f9;
      font-family: MicrosoftYaHei;
      position: absolute;
      text-shadow: 0 0 6px rgba(46, 139, 255, 0.6);
      z-index: 4;
    }

    .ball:nth-child(1) {
      top: 2px;
      right: 166px;
      transform: rotateZ(-45deg) scaleY(2);

      .num {
        font-weight: bold;
        font-size: 15px;
      }

      .text {
        position: absolute;
        left: -34px;
        top: 30px;
        font-size: 10px;
        width: 105px;
        color: #8ec4ff;
        text-shadow: none;
        line-height: 1.2;
      }
    }
    .ball:nth-child(2) {
      top: 80px;
      left: -10px;
      transform: rotateZ(-45deg) scaleY(2);
      .num {
        font-weight: bold;
        font-size: 15px;
      }

      .text {
        position: absolute;
        left: -34px;
        top: 30px;
        font-size: 10px;
        width: 105px;
        color: #8ec4ff;
        text-shadow: none;
        line-height: 1.2;
      }
    }
    .ball:nth-child(3) {
      top: 204px;
      left: 26px;
      transform: rotateZ(-45deg) scaleY(2);
      .num {
        font-weight: bold;
        font-size: 15px;
      }

      .text {
        position: absolute;
        left: -34px;
        top: 30px;
        font-size: 10px;
        width: 105px;
        color: #8ec4ff;
        text-shadow: none;
        line-height: 1.2;
      }
    }
    .ball:nth-child(4) {
      top: 10px;
      left: 190px;
      transform: rotateZ(-45deg) scaleY(2);
      .num {
        font-weight: bold;
        font-size: 15px;
      }

      .text {
        position: absolute;
        left: -34px;
        top: 30px;
        font-size: 10px;
        width: 105px;
        color: #8ec4ff;
        text-shadow: none;
        line-height: 1.2;
      }
    }
    .ball:nth-child(5) {
      top: 230px;
      left: 156px;
      transform: rotateZ(-45deg) scaleY(2);
      .num {
        font-weight: bold;
        font-size: 15px;
      }

      .text {
        position: absolute;
        left: -34px;
        top: 30px;
        font-size: 10px;
        width: 105px;
        color: #8ec4ff;
        text-shadow: none;
        line-height: 1.2;
      }
    }
    .ball:nth-child(6) {
      top: 156px;
      right: 0px;
      transform: rotateZ(-45deg) scaleY(2);
      .num {
        font-weight: bold;
        font-size: 15px;
      }

      .text {
        position: absolute;
        left: -34px;
        top: 30px;
        font-size: 10px;
        width: 105px;
        color: #8ec4ff;
        text-shadow: none;
        line-height: 1.2;
      }
    }

    @keyframes planet-rotate {
      0% {
        transform: scaleY(0.5) rotate(0deg);
      }
      100% {
        transform: scaleY(0.5) rotate(360deg);
      }
    }
    @keyframes self-rotate {
      0% {
        transform: rotate(0deg) scaleY(2);
      }
      100% {
        transform: rotate(-360deg) scaleY(2);
      }
    }
    @keyframes drone-float {
      0%, 100% {
        transform: translateY(0px) scale(1);
      }
      50% {
        transform: translateY(-8px) scale(1.03);
      }
    }
    .planet {
      animation: planet-rotate 20s linear infinite;
    }
    .ball {
      animation: self-rotate 20s linear infinite;
    }
  }
}
</style>
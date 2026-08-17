<template>
  <div class="device-manage">
    <view-title title="设备管理" />

    <div class="user-row" :class="{ 'auto-height': useAutoHeight }">
      <div class="top" :class="{ 'no-scroll': useAutoHeight }">
        <div class="summary-row">
          <div class="summary-item">
            <div class="summary-icon summary-icon-airport" title="机场"></div>
            <div class="summary-body">
              <div class="summary-value">{{ airportCount }}</div>
              <div class="summary-label">机场数量</div>
            </div>
          </div>
          <div class="summary-item">
            <div class="summary-icon summary-icon-plane" title="无人机"></div>
            <div class="summary-body">
              <div class="summary-value">{{ uavCount }}</div>
              <div class="summary-label">飞机数量</div>
            </div>
          </div>
        </div>

        <div class="device-list">
          <div
            v-for="(item, index) in deviceList"
            :key="index"
            class="device-item"
            :class="[
              item.status,
              { onlineHighlight: item.status !== 'offline' },
              { selected: item.kind === 'uav' && store.selectedSn === item.sn }
            ]"
            @click="handleItemClick(item)"
          >
            <div class="device-status">
              <span class="status-dot" :class="item.status"></span>
            </div>

            <div class="device-icon" :class="item.kind === 'airport' ? 'device-icon-airport' : 'device-icon-plane'" :title="item.kind === 'airport' ? '机场' : '无人机'"></div>

            <div class="device-name">
              {{ item.name }}
            </div>

            <div class="device-mode" v-if="item.kind === 'uav'">
              <span class="mode-tag" :class="store.modeClass(getUavMode(item))">
                <span class="mode-name">{{ store.modeText(getUavMode(item)) }}</span>
              </span>
            </div>

            <div class="airport-status" v-else>
              <span class="status-tag" :class="getAirportStatusClass(item.flighttaskStepCode)">
                <span class="status-dot-small" :class="getAirportStatusClass(item.flighttaskStepCode)"></span>
                <span class="status-text">{{ getAirportTaskText(item.flighttaskStepCode) }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import viewTitle from './-title.vue'
import { useAppStore, type UavMode, getAirportTaskText } from '@/store'
import { deviceApi, type DeviceDTO } from '@/api/uav'

const emit = defineEmits(['select'])

const store = useAppStore()

const deviceList = computed(() => {
  return [...store.devices].sort((a, b) => {
    const aOnline = a.status !== 'offline'
    const bOnline = b.status !== 'offline'
    if (aOnline !== bOnline) {
      return aOnline ? -1 : 1
    }
    if (aOnline && bOnline) {
      const aIsUav = a.kind === 'uav'
      const bIsUav = b.kind === 'uav'
      if (aIsUav !== bIsUav) {
        return aIsUav ? -1 : 1
      }
    }
    return 0
  })
})

const airportCount = computed(() => store.deviceStats.airportCount)
const uavCount = computed(() => store.deviceStats.uavCount)

// 判断是否使用自适应高度（设备少时）
const useAutoHeight = computed(() => {
  const count = deviceList.value.length
  // 假设每个设备项57px，间距7px，标题+统计栏约120px
  // 当总高度小于500px时，使用自适应高度
  const estimatedHeight = 120 + count * (57 + 7)
  return estimatedHeight < 500
})

const uavModeMap = computed<Record<string, UavMode>>(() => {
  const map: Record<string, UavMode> = {}
  Object.keys(store.uavStatusMap).forEach(sn => {
    const status = store.uavStatusMap[sn]
    if (status.mode !== undefined && status.mode !== null) {
      map[sn] = status.mode
    }
  })
  return map
})

const getUavMode = (item: DeviceDTO): UavMode | null => {
  if (item.kind !== 'uav') return null
  const mode = uavModeMap.value[item.sn]
  if (mode !== undefined && mode !== null) return mode
  return null
}

const getAirportStatusClass = (code?: number): string => {
  if (code === 1) return 'flying'
  if (code === 0 || code === 2 || code === 3 || code === 4) return 'online'
  return 'offline'
}

const handleItemClick = (item: DeviceDTO) => {
  if (item.kind === 'uav') {
    store.selectUav(item.sn)
    if (item.lng && item.lat) {
      emit('select', item)
    }
  } else if (item.lng && item.lat) {
    emit('select', item)
  }
}

onMounted(() => {})

onUnmounted(() => {})
</script>

<style lang="less" scoped>
:host {
  width: 100%;
  height: 100%;
  display: block;
}

.device-manage {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;

  .user-row {
    position: relative;
    background: rgba(23, 39, 66, 0.5);
    border: 1px solid;
    border-image: linear-gradient(
        180deg,
        rgba(255, 255, 255, 0),
        rgba(73, 123, 207, 1)
      )
      1 1;
    width: 100%;
    flex: 1;
    min-height: 0;

    &.auto-height {
      flex: none;
      min-height: 0;
      height: auto;
    }

    .top {
      padding: 14px 32px 0;
      height: 100%;
      overflow-y: auto;

      &.no-scroll {
        height: auto;
        overflow-y: visible;
      }
      box-sizing: border-box;
      scrollbar-width: thin;
      scrollbar-color: rgba(46, 139, 255, 0.55) rgba(46, 139, 255, 0.06);

      &::-webkit-scrollbar {
        width: 6px;
        background: rgba(46, 139, 255, 0.06);
        border-radius: 3px;
      }
      &::-webkit-scrollbar-thumb {
        background: rgba(46, 139, 255, 0.55);
        border-radius: 3px;
        transition: all 0.2s;
      }
      &::-webkit-scrollbar-thumb:hover {
        background: rgba(46, 139, 255, 0.85);
      }
      &::-webkit-scrollbar-track {
        background: rgba(46, 139, 255, 0.06);
        border-radius: 3px;
      }

      .summary-row {
        display: flex;
        gap: 12px;
        margin-bottom: 14px;

        .summary-item {
          flex: 1;
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 10px 12px;
          background: linear-gradient(135deg, rgba(46, 139, 255, 0.18), rgba(46, 139, 255, 0.04));
          border: 1px solid rgba(46, 139, 255, 0.45);
          border-radius: 6px;
          box-shadow: 0 0 12px rgba(46, 139, 255, 0.1), inset 0 0 16px rgba(46, 139, 255, 0.05);

          .summary-icon {
            flex-shrink: 0;
            width: 56px;
            height: 56px;
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center;
            filter: drop-shadow(0 0 8px rgba(46, 139, 255, 0.7));
          }
          .summary-icon-airport {
            background-image: url('./img/airport-white.png');
          }
          .summary-icon-plane {
            background-image: url('./img/plane.png');
            filter: drop-shadow(0 0 10px rgba(46, 139, 255, 0.85));
          }

          .summary-body {
            flex: 1;
            min-width: 0;

            .summary-value {
              font-size: 24px;
              font-weight: 700;
              color: #ffffff;
              line-height: 1.1;
              font-family: 'DIN Alternate', monospace;
              letter-spacing: 1px;
              text-shadow: 0 0 10px rgba(46, 139, 255, 0.7);
            }

            .summary-label {
              margin-top: 4px;
              font-size: 12px;
              color: #8fb8ff;
              letter-spacing: 1px;
            }
          }
        }
      }

      .device-list {
        min-height: 0;
        display: flex;
        flex-direction: column;

        .device-item {
          height: 57px !important;
          min-height: 57px !important;
          flex-shrink: 0;
          display: flex;
          align-items: center;
          background-image: url('./img/dangerBg.png');
          background-size: 100% 100%;
          background-repeat: no-repeat;
          margin-top: 7px;
          cursor: pointer;
          transition: transform 0.2s ease, filter 0.2s ease;
          opacity: 0.45;

          &.offline {
            .device-name {
              color: #9aa9c8;
            }
          }

          &.onlineHighlight {
            opacity: 1;

            &:hover {
              transform: translateX(4px);
              filter: drop-shadow(0 0 8px rgba(46, 139, 255, 0.45));
            }
          }

          &.selected {
            transform: translateX(3px);

            &::before {
              content: '';
              display: block;
              width: 3px;
              height: 14px;
              background: linear-gradient(180deg, #5cc8ff, #2e8bff);
              border-radius: 2px;
              box-shadow: 0 0 6px rgba(92, 200, 255, 0.9);
              flex-shrink: 0;
              margin-right: -2px;
            }
          }

          .device-status {
            flex-shrink: 0;
            margin-left: 10px;

            .status-dot {
              display: inline-block;
              width: 8px;
              height: 8px;
              border-radius: 50%;

              &.online {
                background: #52c41a;
                box-shadow: 0 0 8px rgba(82, 196, 26, 0.9);
              }
              &.flying {
                background: #2e8bff;
                box-shadow: 0 0 8px rgba(46, 139, 255, 0.95);
                animation: blink 1.2s ease-in-out infinite;
              }
              &.offline {
                background: #8c8c8c;
                box-shadow: none;
              }
            }
          }

          .device-icon {
            flex-shrink: 0;
            width: 28px;
            height: 28px;
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center;
            filter: drop-shadow(0 0 4px rgba(46, 139, 255, 0.6));
            margin-left: 8px;
          }
          .device-icon-plane {
            background-image: url('./img/plane.png');
          }
          .device-icon-airport {
            background-image: url('./img/airport-white.png');
          }

          .airport-status {
            flex-shrink: 0;
            margin-left: auto;
            margin-right: 10px;

            .status-tag {
              display: flex;
              align-items: center;
              gap: 4px;
              padding: 3px 7px;
              border-radius: 10px;
              border: 1px solid;
              font-size: 10px;
              font-weight: 700;
              letter-spacing: 0.3px;

              &.online, &.flying {
                background: rgba(82, 196, 26, 0.22);
                border-color: rgba(82, 196, 26, 0.72);
                color: #73d13d;
                box-shadow: 0 0 6px rgba(82, 196, 26, 0.32);
              }
              &.offline {
                background: rgba(140, 140, 140, 0.22);
                border-color: rgba(140, 140, 140, 0.55);
                color: #bfbfbf;
                box-shadow: none;
              }

              .status-dot-small {
                width: 5px;
                height: 5px;
                border-radius: 50%;
                &.online, &.flying {
                  background: #73d13d;
                  box-shadow: 0 0 4px rgba(115, 209, 61, 0.85);
                  animation: blink 1.6s ease-in-out infinite;
                }
                &.offline {
                  background: #8c8c8c;
                }
              }
              .status-text {
                color: inherit;
                white-space: nowrap;
              }
            }
          }

          .device-name {
            flex: 1;
            min-width: 0;
            margin-left: 8px;
            font-size: 12px;
            font-weight: 600;
            color: #ffffff;
            letter-spacing: 0.3px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            text-shadow: 0 0 4px rgba(46, 139, 255, 0.4);
          }

          .device-mode {
            flex-shrink: 0;
            margin-left: auto;
            margin-right: 10px;
            .mode-tag {
              display: flex;
              align-items: center;
              gap: 3px;
              padding: 2px 7px;
              border-radius: 8px;
              border: 1px solid;
              font-size: 10px;
              font-weight: 700;
              letter-spacing: 0.3px;
              .mode-icon { font-size: 11px; }
              .mode-name { color: #fff; white-space: nowrap; }

              &.mode-auto { background: rgba(46,139,255,0.22); border-color: rgba(46,139,255,0.72); color: #5ca8ff; box-shadow: 0 0 6px rgba(46,139,255,0.32); }
              &.mode-guided { background: rgba(155,89,255,0.22); border-color: rgba(155,89,255,0.72); color: #b48cff; box-shadow: 0 0 6px rgba(155,89,255,0.32); }
              &.mode-rtl { background: rgba(250,173,20,0.22); border-color: rgba(250,173,20,0.72); color: #ffc53d; box-shadow: 0 0 6px rgba(250,173,20,0.32); }
              &.mode-land { background: rgba(255,77,79,0.22); border-color: rgba(255,77,79,0.72); color: #ff7875; box-shadow: 0 0 6px rgba(255,77,79,0.32); }
              &.mode-takeoff { background: rgba(82,196,26,0.22); border-color: rgba(82,196,26,0.72); color: #73d13d; box-shadow: 0 0 6px rgba(82,196,26,0.32); }
              &.mode-loiter { background: rgba(19,194,194,0.22); border-color: rgba(19,194,194,0.72); color: #36cfc9; box-shadow: 0 0 6px rgba(19,194,194,0.32); }
              &.mode-standby { background: rgba(140,140,140,0.22); border-color: rgba(140,140,140,0.55); color: #bfbfbf; box-shadow: none; }
              &.mode-manual { background: rgba(255,158,43,0.22); border-color: rgba(255,158,43,0.72); color: #ffa940; box-shadow: 0 0 6px rgba(255,158,43,0.32); }
              &.mode-offline { background: rgba(140,140,140,0.22); border-color: rgba(140,140,140,0.55); color: #bfbfbf; box-shadow: none; }
            }
          }
        }
      }
    }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
</style>

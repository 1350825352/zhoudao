<template>
  <div class="connector-pie">
    <view-title title="警情统计" />
    <div ref="chartRef" class="pie-chart"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import viewTitle from './-title.vue'

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

const mockData = [
  { value: 35, name: '严重警情' },
  { value: 45, name: '一般警情' },
  { value: 20, name: '预警信息' },
  { value: 15, name: '提示信息' }
]

const colors = ['#ff4d4f', '#ff7a45', '#2e8bff', '#52c41a']

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(5, 15, 44, 0.9)',
      borderColor: '#2e8bff',
      textStyle: {
        color: '#ffffff'
      }
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: {
        color: '#8fb8ff',
        fontSize: 13
      },
      itemWidth: 12,
      itemHeight: 12,
      itemGap: 16
    },
    color: colors,
    series: [
      {
        name: '警情类型',
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 4,
          borderColor: 'rgba(5, 15, 44, 0.8)',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
            color: '#ffffff',
            formatter: '{b}\n{d}%'
          },
          itemStyle: {
            shadowBlur: 20,
            shadowColor: 'rgba(46, 139, 255, 0.5)'
          }
        },
        labelLine: {
          show: false
        },
        data: mockData
      }
    ]
  }

  chartInstance.setOption(option)
}

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style lang="less" scoped>
.connector-pie {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;

  .pie-chart {
    flex: 1;
    min-height: 0;
    width: 100%;
    padding: 0 12px 12px;
    box-sizing: border-box;
  }
}
</style>

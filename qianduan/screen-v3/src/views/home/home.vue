<template>
  <div class="screen-home">
    <header class="screen-header">
      <myHeader />
    </header>

    <main class="screen-main">
      <section class="center-col">
        <mainMap ref="mainMapRef" :pointInfo="pointInfo" @open-video="handleOpenVideo" />
      </section>

      <aside class="side-col left-col">
        <div class="panel-box">
          <droneRealtime />
        </div>
        <div class="panel-box">
          <dangerSum @point="handlePoint" />
        </div>
      </aside>

      <aside class="side-col right-col">
        <div class="panel-box">
          <deviceManage @select="handleDeviceSelect" />
        </div>
        <div class="panel-box">
          <lineManage />
        </div>
      </aside>
    </main>

    <!-- Video Container for full screen overlay -->
    <Teleport to="body">
      <div class="video-container" :class="{ 'two-videos': playingVideos.length === 2 }" v-if="playingVideos.length > 0">
        <div
          v-for="(video, index) in playingVideos"
          :key="video.id"
          class="video-window"
          :data-video-id="video.id"
        >
          <div class="video-header">
            <span class="video-title">{{ video.title }}</span>
            <span class="video-close" @click.stop="closeVideo(video.id)">✕</span>
          </div>
          <video
            :ref="(el) => setVideoRef(video.id, el)"
            controls
            autoplay
            muted
            playsinline
            class="video-player"
            :style="video.ratio ? { aspectRatio: video.ratio } : {}"
            @loadedmetadata="(e) => onVideoLoaded(e, video.id)"
          ></video>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import myHeader from './-header.vue'
import droneRealtime from './droneRealtime.vue'
import dangerSum from './dangerSum.vue'
import deviceManage from './deviceManage.vue'
import lineManage from './lineManage.vue'
import mainMap from './mainMap.vue'
import { useAppStore } from '@/store'
import { deviceApi } from '@/api/uav'
import flvjs from 'flv.js'

const store = useAppStore()
const pointInfo = ref()
const mainMapRef = ref<InstanceType<typeof mainMap> | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null
let statsTimer: ReturnType<typeof setInterval> | null = null

interface PlayingVideo {
  id: string
  url: string
  whepUrl?: string
  title: string
  ratio?: number
  player?: flvjs.Player
}

const playingVideos = ref<PlayingVideo[]>([])
const flvPlayers = ref<Map<string, flvjs.Player>>(new Map())
const rtcPlayers = ref<Map<string, { pc: RTCPeerConnection; stream?: MediaStream }>>(new Map())
const videoRefs = ref<Map<string, HTMLVideoElement>>(new Map())

const setVideoRef = (id: string, el: HTMLVideoElement | null) => {
  if (el) {
    videoRefs.value.set(id, el)
  } else {
    videoRefs.value.delete(id)
  }
}

const onVideoLoaded = (e: Event, id: string) => {
  const videoEl = e.target as HTMLVideoElement
  if (videoEl.videoWidth && videoEl.videoHeight) {
    const ratio = videoEl.videoWidth / videoEl.videoHeight
    const video = playingVideos.value.find(v => v.id === id)
    if (video) {
      video.ratio = ratio
    }
  }
}

const initFlvPlayer = (id: string, url: string) => {
  if (!flvjs.isSupported()) {
    ElMessage.error('浏览器不支持flv.js')
    return
  }
  let videoEl = videoRefs.value.get(id)
  if (!videoEl) {
    const el = document.querySelector(`.video-window[data-video-id="${id}"] video`)
    if (el) {
      videoEl = el as HTMLVideoElement
      videoRefs.value.set(id, videoEl)
    } else {
      ElMessage.error('视频元素未找到')
      return
    }
  }
  const existingPlayer = flvPlayers.value.get(id)
  if (existingPlayer) {
    existingPlayer.destroy()
  }
  const player = flvjs.createPlayer({
    type: 'flv',
    url: url,
    isLive: true,
    hasAudio: false,
    hasVideo: true,
    enableStashBuffer: true,
    stashInitialSize: 256,
    autoCleanupSourceBuffer: true,
    autoCleanupBackwardDuration: 60,
    autoCleanupForwardDuration: 30,
    fixAudioTimestampGap: false,
    cors: true
  })
  player.on(flvjs.Events.ERROR, (err: any) => {
    console.error('[FlvPlayer] error', id, err)
    ElMessage.error(`视频播放错误: ${err.message}`)
  })
  player.attachMediaElement(videoEl)
  player.load()
  player.play().catch(e => {
    console.error('[FlvPlayer] play failed', id, e)
  })
  flvPlayers.value.set(id, player)
}

/**
 * 使用 WebRTC(WHEP) 播放视频流 - SRS 官方 WHEP 协议
 * 流程: 客户端createOffer → setLocal → POST offer.sdp → 服务器返回answer → setRemote
 * 优先使用,低延迟无缓冲圈圈
 */
const initRtcPlayer = async (id: string, whepUrl: string, flvFallbackUrl?: string) => {
  let videoEl = videoRefs.value.get(id)
  if (!videoEl) {
    const el = document.querySelector(`.video-window[data-video-id="${id}"] video`)
    if (el) {
      videoEl = el as HTMLVideoElement
      videoRefs.value.set(id, videoEl)
    } else {
      ElMessage.error('视频元素未找到')
      return
    }
  }
  // 清理之前的播放器
  const oldFlv = flvPlayers.value.get(id)
  if (oldFlv) {
    oldFlv.destroy()
    flvPlayers.value.delete(id)
  }
  const oldRtc = rtcPlayers.value.get(id)
  if (oldRtc) {
    oldRtc.pc.close()
    rtcPlayers.value.delete(id)
  }
  try {
    console.log('[RtcPlayer] 开始 WebRTC(WHEP) 播放', id, whepUrl)
    // Step 1: 创建 RTCPeerConnection
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require'
    })
    const remoteStream = new MediaStream()
    pc.ontrack = (e) => {
      e.streams.forEach(s => {
        s.getTracks().forEach(t => {
          if (!remoteStream.getTracks().find(tt => tt.id === t.id)) {
            remoteStream.addTrack(t)
          }
        })
      })
      if (videoEl && videoEl.srcObject !== remoteStream) {
        videoEl.srcObject = remoteStream
        videoEl.play().catch(e => console.warn('[RtcPlayer] play catch', e))
      }
    }
    pc.onconnectionstatechange = () => {
      console.log('[RtcPlayer] connectionState:', pc.connectionState, id)
      if (pc.connectionState === 'failed' || pc.connectionState === 'disconnected') {
        console.warn('[RtcPlayer] 连接失败,降级到 FLV', id)
        pc.close()
        rtcPlayers.value.delete(id)
        if (flvFallbackUrl) {
          ElMessage.warning('WebRTC 连接失败,已降级到 FLV 播放')
          initFlvPlayer(id, flvFallbackUrl)
        }
      }
    }
    pc.oniceconnectionstatechange = () => {
      console.log('[RtcPlayer] iceConnectionState:', pc.iceConnectionState, id)
    }
    // Step 2: 添加接收方向的 transceiver (只收不发)
    pc.addTransceiver('audio', { direction: 'recvonly' })
    pc.addTransceiver('video', { direction: 'recvonly' })
    // Step 3: 客户端创建 Offer
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    console.log('[RtcPlayer] 生成本地 SDP Offer, 准备发送给 SRS 服务器')
    // Step 4: POST 发送 offer.sdp 到 WHEP URL, 获取 answer.sdp
    const answerSdp = await new Promise<string>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.onload = function() {
        if (xhr.readyState !== xhr.DONE) return
        if (xhr.status !== 200 && xhr.status !== 201) {
          reject(new Error(`WHEP 服务器返回: ${xhr.status}`))
          return
        }
        const data = xhr.responseText
        if (!data || data.indexOf('v=') < 0) {
          reject(new Error('WHEP Answer SDP 无效'))
          return
        }
        resolve(data)
      }
      xhr.onerror = function() { reject(new Error('WHEP 请求网络错误')) }
      xhr.open('POST', whepUrl, true)
      xhr.setRequestHeader('Content-type', 'application/sdp')
      xhr.send(offer.sdp)
    })
    console.log('[RtcPlayer] 收到 SRS 返回的 SDP Answer')
    // Step 5: 设置远端 Description (answer)
    await pc.setRemoteDescription(
      new RTCSessionDescription({ type: 'answer', sdp: answerSdp })
    )
    rtcPlayers.value.set(id, { pc, stream: remoteStream })
    console.log('[RtcPlayer] WebRTC 播放启动成功', id)
  } catch (e: any) {
    console.error('[RtcPlayer] WebRTC 播放失败,降级到 FLV', id, e)
    if (flvFallbackUrl) {
      ElMessage.warning(`WebRTC 播放失败(${e.message}),已降级到 FLV 播放`)
      initFlvPlayer(id, flvFallbackUrl)
    } else {
      ElMessage.error(`视频播放失败: ${e.message}`)
    }
  }
}

const handleOpenVideo = (video: { id: string; url: string; whepUrl?: string; title: string }) => {
  const existing = playingVideos.value.find(v => v.id === video.id)
  if (!existing) {
    playingVideos.value.push(video)
    playingVideos.value.sort((a, b) => {
      if (a.id === 'live') return -1
      if (b.id === 'live') return 1
      return 0
    })
    setTimeout(() => {
      // 优先 WebRTC(WHEP) 播放,低延迟无缓冲;失败自动降级 FLV
      if (video.whepUrl) {
        initRtcPlayer(video.id, video.whepUrl, video.url)
      } else {
        initFlvPlayer(video.id, video.url)
      }
    }, 100)
  }
}

const closeVideo = (id: string) => {
  const flvPlayer = flvPlayers.value.get(id)
  if (flvPlayer) {
    flvPlayer.destroy()
    flvPlayers.value.delete(id)
  }
  const rtcPlayer = rtcPlayers.value.get(id)
  if (rtcPlayer) {
    rtcPlayer.pc.close()
    rtcPlayers.value.delete(id)
  }
  videoRefs.value.delete(id)
  playingVideos.value = playingVideos.value.filter(v => v.id !== id)
}

const handlePoint = (e: any) => {
  pointInfo.value = { ...e }
  // 如果是告警信息，在地图上显示标记
  if (e && typeof e.lng === 'number' && typeof e.lat === 'number') {
    // 通过DOM操作调用mainMap中的showAlertMarker函数
    const mapEl = document.getElementById('tiandituMap')
    if (mapEl) {
      const showAlertFn = (window as any).showAlertMarker
      if (typeof showAlertFn === 'function') {
        showAlertFn(e.lng, e.lat, e.name || '告警位置')
      }
    }
  }
}

// 设备选择处理（只更新pointInfo，不触发告警图标）
const handleDeviceSelect = (e: any) => {
  pointInfo.value = { ...e }
}

const loadData = async () => {
  try {
    const devicesRes = await deviceApi.getDeviceList()
    store.updateDevices(devicesRes.data)
    
    if (!store.selectedSn) {
      const firstUav = store.devices.find(d => d.kind === 'uav')
      if (firstUav) {
        store.selectUav(firstUav.sn)
        if (firstUav.lng && firstUav.lat) {
          pointInfo.value = { ...firstUav }
        }
      }
    }

    const uavs = store.devices.filter(d => d.kind === 'uav')
    if (uavs.length > 0) {
      await Promise.all(uavs.map(uav => 
        deviceApi.getUavStatus(uav.sn).then(res => {
          store.updateUavStatus(uav.sn, res.data)
        }).catch(() => {})
      ))
    }
  } catch (e) {
    console.warn('加载设备数据失败', e)
  }
}

const loadStats = async () => {
  try {
    const statsRes = await deviceApi.getDeviceStats()
    store.updateDeviceStats(statsRes.data)
  } catch (e) {
    console.warn('加载设备统计失败', e)
  }
}

onMounted(() => {
  loadStats()
  statsTimer = setInterval(loadStats, 12 * 60 * 60 * 1000)
  loadData()
  refreshTimer = setInterval(loadData, 500)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  if (statsTimer) {
    clearInterval(statsTimer)
  }
})
</script>

<style lang="less" scoped>
.screen-home {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #050f2c 0%, #0a1a4a 50%, #081236 100%);
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.screen-header {
  width: 100%;
  height: 80px;
  flex-shrink: 0;
  z-index: 10;
  position: relative;
}

.screen-main {
  flex: 1;
  width: 100%;
  height: calc(100vh - 80px);
  position: relative;
  z-index: 5;
}

.center-col {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  min-width: 0;

  :deep(> *) {
    width: 100%;
    height: 100%;
    display: block;
  }
}

.side-col {
  position: absolute;
  z-index: 5;
  width: 460px;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: 100%;
  align-self: stretch;

  .panel-box {
    flex: 1 1 0;
    min-height: 0;
    width: 100%;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    :deep(> *) {
      width: 100%;
      height: 100%;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }
  }
}

.left-col {
  left: 0;

  .panel-box {
    flex: 1;
    min-height: 0;
    max-height: none;
  }
}

.right-col {
  right: 0;

  .panel-box {
    flex: 1;
    min-height: 0;
    max-height: none;
  }
}

@media (max-width: 1440px) {
  .side-col {
    width: 23.96%;
  }
}

@media (max-width: 1000px) {
  .side-col {
    width: 20%;
  }
}
</style>

<style lang="less">
.video-container {
  z-index: 99999;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  pointer-events: none;
  overflow: hidden;

  &.two-videos {
    .video-window {
      width: 42vw;
      max-width: 600px;
    }
  }

  .video-window {
    width: 60vw;
    max-width: 800px;
    background: rgba(23, 39, 66, 0.98);
    border: 1px solid rgba(46, 139, 255, 0.6);
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 0 30px rgba(46, 139, 255, 0.4), inset 0 0 40px rgba(46, 139, 255, 0.1);
    pointer-events: auto;

    .video-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px 16px;
      background: linear-gradient(90deg, rgba(46, 139, 255, 0.2), rgba(46, 139, 255, 0.05));
      border-bottom: 1px solid rgba(46, 139, 255, 0.3);

      .video-title {
        font-size: 14px;
        font-weight: 700;
        color: #ffffff;
        letter-spacing: 1px;
        text-shadow: 0 0 8px rgba(46, 139, 255, 0.6);
      }

      .video-close {
        width: 28px;
        height: 28px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 16px;
        color: #8ec4ff;
        cursor: pointer;
        border-radius: 6px;
        transition: all 0.2s;

        &:hover {
          background: rgba(255, 77, 79, 0.2);
          color: #ff7875;
        }
      }
    }

    .video-player {
      width: 100%;
      height: auto;
      aspect-ratio: 16/9;
      background: #000;
      display: block;
      object-fit: contain;
    }
  }
}

@media (max-width: 1400px) {
  .video-container {
    .video-window {
      width: 55vw;
      max-width: 700px;
    }
    &.two-videos .video-window {
      width: 40vw;
      max-width: 500px;
    }
    .video-title {
      font-size: 13px;
    }
    .video-close {
      width: 24px;
      height: 24px;
      font-size: 14px;
    }
  }
}

@media (max-width: 1200px) {
  .video-container {
    .video-window {
      width: 50vw;
      max-width: 600px;
    }
    &.two-videos .video-window {
      width: 38vw;
      max-width: 450px;
    }
    .video-title {
      font-size: 12px;
    }
    .video-close {
      width: 22px;
      height: 22px;
      font-size: 13px;
    }
  }
}

@media (max-width: 1000px) {
  .video-container {
    .video-window {
      width: 70vw;
      max-width: 500px;
    }
    &.two-videos .video-window {
      width: 32vw;
      max-width: 380px;
    }
    .video-header {
      padding: 8px 12px;
    }
    .video-title {
      font-size: 11px;
    }
    .video-close {
      width: 20px;
      height: 20px;
      font-size: 12px;
    }
  }
}
</style>

# 大屏前端接口规范文档

## 一、接口概述

本文档定义了无人机数据大屏（screen-v3）前端与后端对接的 RESTful API 接口规范。

### 统一响应格式

所有接口返回统一格式：

```json
{
  "code": 200,
  "data": {},
  "message": "success"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `number` | 200 表示成功，其他值表示失败 |
| `data` | `any` | 业务数据 |
| `message` | `string` | 提示信息 |

---

## 二、接口列表

### 1. 设备列表接口

**用途**：获取右栏 R1 设备管理列表数据（飞机和机场）

| 属性 | 值 |
|------|-----|
| **URL** | `GET /devices/list` |
| **请求参数** | 无 |

**响应数据结构 - DeviceDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sn` | `string` | 设备序列号（唯一标识） |
| `name` | `string` | 设备名称 |
| `kind` | `'uav' \| 'airport'` | 设备类型：飞机/机场 |
| `status` | `'online' \| 'offline' \| 'flying'` | 设备状态 |
| `lng` | `number` (可选) | 经度坐标（双精度浮点数，支持小数点后多位） |
| `lat` | `number` (可选) | 纬度坐标（双精度浮点数，支持小数点后多位） |
| `flighttaskStepCode` | `number` (可选) | 机场任务状态码（仅机场设备有效，详见下表） |

**机场任务状态码说明**（flighttaskStepCode 字段）：

| 状态码 | 中文描述 | 说明 |
|--------|----------|------|
| `0` | 作业准备中 | 机场正在准备作业 |
| `1` | 飞行作业中 | 飞机正在执行飞行作业 |
| `2` | 作业后状态恢复 | 作业完成后恢复状态 |
| `3` | 自定义飞行区更新中 | 自定义飞行区更新 |
| `4` | 地形障碍物更新中 | 地形障碍物更新 |
| `5` | 任务空闲 | 无任务执行 |
| `255` | 飞行器异常 | 飞行器出现异常 |
| `256` | 未知状态 | 状态未知 |

> **注**：如果后端返回的 flighttaskStepCode 值不在上述范围内，前端会显示"其他状态"。

**响应示例**：

```json
{
  "code": 200,
  "data": [
    {"sn": "SN001", "name": "UAV-001", "kind": "uav", "status": "flying", "lng": 118.7968771234, "lat": 32.0602556789},
    {"sn": "SN002", "name": "UAV-002", "kind": "uav", "status": "online", "lng": 118.8108774321, "lat": 32.0702559876},
    {"sn": "SN003", "name": "UAV-003", "kind": "uav", "status": "offline", "lng": 118.7828775678, "lat": 32.0522551234},
    {"sn": "AIR001", "name": "机场-基地A", "kind": "airport", "status": "online", "lng": 118.7900000000, "lat": 32.0550000000, "flighttaskStepCode": 1},
    {"sn": "AIR002", "name": "机场-指挥中心", "kind": "airport", "status": "offline", "lng": 118.7750000000, "lat": 32.0450000000, "flighttaskStepCode": 5}
  ],
  "message": "success"
}
```

---

### 2. 设备统计接口

**用途**：获取右栏 R1 顶部统计数据（飞机数量、机场数量）

| 属性 | 值 |
|------|-----|
| **URL** | `GET /devices/stats` |
| **请求参数** | 无 |

**响应数据结构 - DeviceStatsDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `uavCount` | `number` | 飞机总数 |
| `airportCount` | `number` | 机场总数 |
| `onlineCount` | `number` | 在线设备数 |
| `flyingCount` | `number` | 飞行中飞机数 |

**响应示例**：

```json
{
  "code": 200,
  "data": {"uavCount": 3, "airportCount": 2, "onlineCount": 4, "flyingCount": 1},
  "message": "success"
}
```

---

### 3. 飞机实时状态接口

**用途**：点击 R1 飞机后，获取左栏 L1 显示的实时状态数据

| 属性 | 值 |
|------|-----|
| **URL** | `GET /uavs/{sn}/status` |
| **路径参数** | `sn` - 飞机序列号 |

**响应数据结构 - UavStatusDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sn` | `string` | 飞机序列号 |
| `name` | `string` | 飞机名称 |
| `status` | `'online' \| 'offline' \| 'flying'` | 状态 |
| `mode` | `number` | 飞行模式（0-21，详见下表） |
| `modeText` | `string` | 模式中文描述 |
| `height` | `number` | 高度（米） |
| `speed` | `number` | 速度（m/s） |
| `battery` | `number` | 电量（%） |
| `satelliteCount` | `number` | 卫星数量 |
| `lng` | `number` | 当前经度（双精度浮点数） |
| `lat` | `number` | 当前纬度（双精度浮点数） |

**响应示例**：

```json
{
  "code": 200,
  "data": {
    "sn": "SN001",
    "name": "UAV-001",
    "status": "flying",
    "mode": 5,
    "modeText": "航线飞行",
    "height": 120.5,
    "speed": 8.2,
    "battery": 75,
    "satelliteCount": 12,
    "lng": 118.7968771234,
    "lat": 32.0602556789
  },
  "message": "success"
}
```

**飞行模式说明**（mode 字段为整数类型）：

| 模式值 | 中文描述 | 说明 |
|--------|----------|------|
| `0` | 待机 | 无人机处于待机状态 |
| `1` | 起飞准备 | 无人机正在准备起飞 |
| `2` | 起飞准备完毕 | 起飞准备已完成 |
| `3` | 手动飞行 | 操作员手动控制飞行 |
| `4` | 自动起飞 | 无人机自动起飞 |
| `5` | 航线飞行 | 按照预设航线自动飞行 |
| `6` | 全景拍照 | 执行全景拍照任务 |
| `7` | 智能跟随 | 智能跟随目标 |
| `8` | ADS-B躲避 | ADS-B 避障模式 |
| `9` | 自动返航 | 自动返回起飞点 |
| `10` | 自动降落 | 自动降落 |
| `11` | 强制降落 | 强制降落 |
| `12` | 三桨叶降落 | 三桨叶降落模式 |
| `13` | 升级中 | 固件升级中 |
| `14` | 未连接 | 未连接状态 |
| `15` | APAS | 高级辅助飞行系统 |
| `16` | 虚拟摇杆状态 | 虚拟摇杆控制 |
| `17` | 指令飞行 | 按指令飞行 |
| `18` | 空中RTK收敛模式 | RTK定位收敛中 |
| `19` | 机场选址中 | 机场选址模式 |
| `20` | POI环绕 | 环绕POI飞行 |
| `21` | 进离场航线飞行过程中 | 进离场航线飞行 |

> **注**：如果后端返回的 mode 值不在上述范围内，前端会显示"未定状态"。

---

### 4. 飞机实时直播视频接口

**用途**：点击 R1 飞机后，获取中央 C 栏「实时直播」按钮的视频链接

| 属性 | 值 |
|------|-----|
| **URL** | `GET /uavs/{sn}/video/live` |
| **路径参数** | `sn` - 飞机序列号 |

**响应数据结构 - LiveVideoUrlDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sn` | `string` | 飞机序列号 |
| `url` | `string \| null` | 实时直播视频链接（无视频时返回 null） |
| `available` | `boolean` | 是否有可用视频 |

**响应示例（有视频）**：

```json
{
  "code": 200,
  "data": {
    "sn": "SN001",
    "url": "http://192.168.2.200:30080/videos/videos/demo.mp4",
    "available": true
  },
  "message": "success"
}
```

**响应示例（无视频）**：

```json
{
  "code": 200,
  "data": {
    "sn": "SN001",
    "url": null,
    "available": false
  },
  "message": "success"
}
```

---

### 5. 飞机实时识别视频接口

**用途**：点击 R1 飞机后，获取中央 C 栏「实时识别」按钮的视频链接

| 属性 | 值 |
|------|-----|
| **URL** | `GET /uavs/{sn}/video/recognition` |
| **路径参数** | `sn` - 飞机序列号 |

**响应数据结构 - RecognitionVideoUrlDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `sn` | `string` | 飞机序列号 |
| `url` | `string \| null` | 实时识别视频链接（无视频时返回 null） |
| `available` | `boolean` | 是否有可用视频 |

**响应示例（有视频）**：

```json
{
  "code": 200,
  "data": {
    "sn": "SN001",
    "url": "http://192.168.2.200:30080/videos/videos/test.mp4",
    "available": true
  },
  "message": "success"
}
```

**响应示例（无视频）**：

```json
{
  "code": 200,
  "data": {
    "sn": "SN001",
    "url": null,
    "available": false
  },
  "message": "success"
}
```

---

### 6. 警情列表接口

**用途**：获取左栏 L2 警情信息列表

| 属性 | 值 |
|------|-----|
| **URL** | `GET /alerts` |
| **请求参数** | 无 |

**响应数据结构 - AlertDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 警情ID |
| `appName` | `string` | 应用名称 |
| `ruleName` | `string` | 规则名称 |
| `alarmTime` | `string` | 报警时间（格式：YYYY-MM-DD HH:mm:ss） |
| `level` | `'high' \| 'medium' \| 'low'` | 告警级别 |

**响应示例**：

```json
{
  "code": 200,
  "data": [
    {"id": "AL001", "appName": "无人机监控", "ruleName": "越界告警", "alarmTime": "2026-07-14 10:30:00", "level": "high"}
  ],
  "message": "success"
}
```

---

### 7. 航线管理接口

**用途**：获取右栏 R2 航线管理列表

| 属性 | 值 |
|------|-----|
| **URL** | `GET /lines` |
| **请求参数** | 无 |

**响应数据结构 - LineDTO**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | 航线ID |
| `fileName` | `string` | 文件名 |
| `actionTypeFmt` | `string` | 动作类型描述 |
| `modiTime` | `string` | 修改时间（格式：YYYY-MM-DD HH:mm:ss） |

**响应示例**：

```json
{
  "code": 200,
  "data": [
    {"id": "LN001", "fileName": "航线A-01", "actionTypeFmt": "自动巡检", "modiTime": "2026-07-14 09:00:00"}
  ],
  "message": "success"
}
```

---

## 三、交互流程

```
1. 用户进入页面 → 前端调用 GET /devices/list 获取设备列表
                          → GET /devices/stats 获取统计数据
                          → GET /alerts 获取警情列表
                          → GET /lines 获取航线列表

2. 前端默认选中第一架飞机 → 调用 GET /uavs/{sn}/status 获取实时状态
                           → 调用 GET /uavs/{sn}/video/live 获取直播视频链接
                           → 调用 GET /uavs/{sn}/video/recognition 获取识别视频链接

3. 用户点击 R1 中某架飞机 → 前端更新选中状态
                         → 调用 GET /uavs/{sn}/status 更新 L1 显示
                         → 调用 GET /uavs/{sn}/video/live 更新直播视频链接
                         → 调用 GET /uavs/{sn}/video/recognition 更新识别视频链接
                         → 地图移动到该飞机坐标位置

4. 用户点击 C 栏「实时直播」 → 如果 url 不为 null，打开视频播放窗口；否则提示"视频暂不可用"
            「实时识别」 → 如果 url 不为 null，打开视频播放窗口；否则提示"视频暂不可用"
```

---

## 四、前端代理配置

在 `vite.config.ts` 中配置了代理：

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://192.168.2.200:30080',  // 后端服务地址
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, '')
    }
  }
}
```

前端请求 `/api/devices/list` 会被代理到 `http://192.168.2.200:30080/devices/list`。

---

## 五、数据类型定义

```typescript
export interface DeviceDTO {
  sn: string
  name: string
  kind: 'uav' | 'airport'
  status: 'online' | 'offline' | 'flying'
  lng?: number
  lat?: number
  flighttaskStepCode?: number
}

export interface DeviceStatsDTO {
  uavCount: number
  airportCount: number
  onlineCount: number
  flyingCount: number
}

export interface UavStatusDTO {
  sn: string
  name: string
  status: 'online' | 'offline' | 'flying'
  mode: number
  modeText: string
  height: number
  speed: number
  battery: number
  satelliteCount: number
  lng: number
  lat: number
}

export interface LiveVideoUrlDTO {
  sn: string
  url: string | null
  available: boolean
}

export interface RecognitionVideoUrlDTO {
  sn: string
  url: string | null
  available: boolean
}

export interface AlertDTO {
  id: string
  appName: string
  ruleName: string
  alarmTime: string
  level: 'high' | 'medium' | 'low'
}

export interface LineDTO {
  id: string
  fileName: string
  actionTypeFmt: string
  modiTime: string
}
```

# 大疆司空2 + 上云API 整合后台服务

基于 Spring Boot 2.7 + MySQL + MyBatis-Plus + Redis + Eclipse Paho MQTT 开发的后台，整合：
1. **大疆上云API** - 通过MQTT实时获取设备在线状态、飞机经纬度、电量、风速、GPS搜星数量等实时数据
2. **大疆司空2 OpenAPI V2.0** - 通过RESTful HTTP调用实现航线同步、飞行任务下发、旁路直播推流等操作

---

## 一、项目结构

```
src/main/java/com/dji/sikong/
├── SikongCloudApiApplication.java     # 启动类
├── common/                             # 公共类
│   ├── R.java                          # 统一返回体
│   ├── DeviceTypeConstants.java        # 设备类型常量
│   └── RedisKeyConstants.java          # Redis Key前缀
├── config/                             # 配置类
│   ├── DjiMqttProperties.java          # MQTT配置(host/用户名/密码等)
│   ├── DjiOpenApiProperties.java       # 司空2 OpenAPI配置
│   ├── DjiMediaProperties.java         # 流媒体服务器配置(旁路推流地址)
│   └── MybatisPlusConfig.java          # MP分页插件+自动填充
├── entity/                             # MyBatis-Plus实体
│   ├── Device.java                     # 设备表(网关/飞机/负载)
│   ├── AircraftCamera.java             # 飞机镜头明细
│   ├── DeviceRealtimeStatus.java       # 设备实时状态(最新一条)
│   ├── Wayline.java                    # 航线信息
│   ├── FlightTask.java                 # 飞行任务
│   ├── LiveStream.java                 # 直播推流
│   └── OrganizationProject.java        # 组织/项目缓存
├── mapper/                             # MyBatis Mapper
├── dto/                                # Controller入参/出参DTO
│   ├── DeviceRealtimeVO.java           # 实时信息VO(8个关键字段)
│   ├── DeviceTopologyVO.java           # 设备拓扑VO(网关->飞机->镜头)
│   ├── AircraftVO.java                 # 飞机明细VO
│   ├── AircraftCameraVO.java           # 镜头VO
│   ├── ExecuteFlightTaskDTO.java       # 执行航线入参
│   └── StartLiveStreamDTO.java         # 开启直播入参
├── service/                            # 业务接口及实现
│   ├── impl/
│   │   ├── DeviceServiceImpl.java      # 设备 & 拓扑 & 实时信息
│   │   ├── DeviceRealtimeStatusServiceImpl.java  # 实时状态解析osd入库+缓存
│   │   ├── WaylineServiceImpl.java     # 航线同步(司空2) + CRUD
│   │   ├── FlightTaskServiceImpl.java  # 执行航线(司空2或MQTT两种方式)
│   │   ├── LiveStreamServiceImpl.java  # 旁路推流 + 失败回退MQTT
│   │   └── OrganizationProjectServiceImpl.java  # 项目同步
│   └── ...Service.java
├── mqtt/
│   └── DjiMqttClient.java              # MQTT客户端核心:
│                                       # - 订阅sys/status thing/osd thing/state...
│                                       # - 处理拓扑+上下线+回复status_reply
│                                       # - 解析osd字段(位置/电量/风速/GPS...)
│                                       # - 回复requests(机场上云必备)
│                                       # - 回复events ACK
│                                       # - 匹配services_reply 到任务/直播
├── openapi/
│   └── DjiOpenApiClient.java           # 司空2 HTTP客户端封装(项目/设备/航线/任务/直播)
└── controller/                         # RESTful接口
    ├── DeviceController.java           # ①实时信息 ⑤设备拓扑(含飞机型号+镜头)
    ├── WaylineController.java          # ②手动同步航线
    ├── FlightTaskController.java       # ③执行航线 POST
    ├── LiveStreamController.java       # ④开启旁路推流
    ├── OrganizationProjectController.java
    └── MqttController.java             # 调试接口
src/main/resources/
├── application.yml                     # 所有配置文件(MQTT/OpenAPI/流媒体/MySQL/Redis)
└── sql/init.sql                        # 数据库建表脚本
pom.xml
```

---

## 二、数据库初始化

执行 `src/main/resources/sql/init.sql` 创建库 `sikong_cloud_api` 及 8 张表。

---

## 三、核心配置说明(`application.yml`)

### 3.1 上云API MQTT配置(实时数据源)
```yaml
dji:
  mqtt:
    host: tcp://127.0.0.1:1883          # MQTT Broker地址(EMQX推荐)
    username: admin                      # MQTT用户名
    password: public                     # MQTT密码
    client-id: sikong-cloud-api-${random.uuid}
    subscribe-topics:
      - sys/product/+/status             # 拓扑+上下线
      - thing/product/+/osd              # 定频实时数据(核心)
      - thing/product/+/state            # 事件实时数据
      - thing/product/+/events
      - thing/product/+/requests         # 机场上云要回复
      - thing/product/+/services_reply   # 匹配任务/直播结果
      - thing/product/+/property/set_reply
```

### 3.2 司空2 OpenAPI配置
```yaml
dji:
  openapi:
    base-url: https://es-flight-api-cn.djigate.com
    user-token: YOUR_X_USER_TOKEN_HERE    # 从司空2后台获取
    language: zh
```

### 3.3 流媒体服务器地址(旁路推流)
```yaml
dji:
  media:
    stream-server:
      rtmp-url: rtmp://your-srs-server/live   # 推流到你的SRS/流媒体
      type: srs
```

---

## 四、对外 RESTful 接口

| # | 方法 | 路径 | 功能 |
|---|------|------|------|
| **1** | GET | `/api/device/realtime/{deviceSn}` | **单个设备实时信息**(在线/经纬度/电量/风速/电池温度/飞行状态/距离Home/GPS搜星) |
| **1** | GET | `/api/device/realtime/list?deviceSns=a,b` | 批量实时信息 |
| **5** | GET | `/api/device/topology` | **设备拓扑(含飞机型号+各镜头型号)** → MySQL存储 |
| **2** | POST | `/api/wayline/sync?projectUuid=` | **前端手动同步航线**(拉司空2航线列表入库) |
| **2** | GET | `/api/wayline/list` | 航线列表 |
| **3** | POST | `/api/flight-task/execute` | **执行航线**: body={aircraftName, taskName, waylineUuid, executeType=1(司空2)/2(MQTT)} |
| **4** | POST | `/api/live-stream/start-bypass` | **开启旁路推流直播**: body={gatewaySn, cameraIndex, qualityType} 自动用yml配置的流媒体地址 |
| * | GET/POST | `/api/project/sync` / `/api/project/list` | 项目同步+列表 |
| * | GET | `/api/mqtt/status` / POST `/api/mqtt/publish` | MQTT调试工具 |

---

## 五、实时信息8个核心字段说明

| 字段 | 来源(MQTT osd) | 说明 |
|------|---------------|------|
| onlineStatus | sys/status 子设备列表 + Redis心跳 | 0=离线,1=在线 |
| latitude / longitude | `data.latitude` / `data.longitude` | 飞机当前经纬度(十进制) |
| batteryPercent | `data.drone_charge_state.capacity_percent` 或 `data.battery_percent` | 0~100 |
| windSpeed | `data.wind_speed` | 风速(米/秒) |
| batteryTemperature | `data.battery.temperature` | 电池温度(℃) |
| flightStatus | `data.flight_status` + `data.device_mode` | 空闲/起飞/飞行/返航/降落/充电等 |
| distanceToHome | `data.distance_to_home` 或 自动用Haversine公式计算 | 距离Home点(米) |
| gpsSatelliteCount | `data.position_state.gps_number` | GPS搜星数 |

---

## 六、启动步骤

1. 准备 MySQL 5.7+/8.0+，执行 `sql/init.sql` 建库建表
2. 准备 Redis(可选，不装也行，代码里都try/catch缓存失败)
3. 准备 EMQX 或其他MQTT Broker(如果是本地测试，可以先起一个 `docker run -p 1883:1883 emqx/emqx`)
4. 修改 `src/main/resources/application.yml`，填入：
   - MySQL用户名/密码
   - MQTT的host/username/password
   - 司空2的X-User-Token
   - 你的流媒体服务器RTMP推流地址
5. 编译 & 启动
```bash
mvn clean package -DskipTests
java -jar target/sikong-cloud-api-1.0.0.jar
```
6. 验证接口
```bash
# 查看MQTT连接状态
curl http://localhost:8080/api/mqtt/status
# 手动同步项目(司空2)
curl -X POST http://localhost:8080/api/project/sync
# 手动同步航线
curl -X POST "http://localhost:8080/api/wayline/sync"
# 设备拓扑
curl http://localhost:8080/api/device/topology
```

---

## 七、关键实现点说明

### 7.1 MQTT自动回复机制(机场上云必备)
在 `DjiMqttClient.handleRequests()` 中针对常见method做了回复逻辑：
| method | 回复内容 |
|--------|---------|
| `config` | License/AppId/AppKey等(上线前改为从DB读取配置) |
| `airport_organization_get` | 绑定码 → 组织映射 |
| `airport_organization_bind` | 绑定结果 |
| `airport_bind_status` | 绑定状态 |
| `storage_config_get` | STS凭证(媒体上传用) |

### 7.2 任务/直播 MQTT request-response匹配
- 下发services时生成唯一tid，缓存 `TASK_TID_BID_KEY + tid → 任务ID`，5分钟过期
- 收到services_reply时按tid查到任务，更新resultCode和状态
- 直播同理

### 7.3 实时状态统一数据源
- MQTT收到osd → 更新MySQL的device_realtime_status表 → 同时写Redis缓存10分钟
- `/device/realtime`接口优先查Redis缓存，再查DB → 避免"推送新但接口返回旧"的割裂问题

### 7.4 执行航线支持两种下发方式
| executeType | 方式 | 适用场景 |
|-------------|------|---------|
| 1 (默认) | 司空2 OpenAPI `POST /flight-task` | 项目绑定在司空2，航线由司空2管理 |
| 2 | 上云API MQTT services `flighttask_prepare` | 直接对接机场，绕开司空2任务模块 |

### 7.5 旁路推流两级回退
1. 先调用司空2 `POST /live-stream/converter` 创建码流转发器(旁路推流)
2. 如果OpenAPI失败，回退到MQTT services `live_start_push` 直接让机场推

---

## 八、后续可扩展项

1. 接入WebSocket，把osd数据实时推到前端大屏
2. 新增定时任务，轮询飞行任务最终状态(司空2 /mqtt)
3. 把 requests/config等回复参数改为管理后台配置(DB+Redis)
4. 媒体资源下载/建模任务管理
5. DRC远程控制接入(独立Broker)

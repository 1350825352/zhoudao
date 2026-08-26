# 阶段3：MinIO 图片同步处理方案（设计文档v2 - 已验证版）

> 设计基准时间：2026-08-26
> 验证方式：使用 `src/main/resources/application-company-a.yml` 真实 Token + PROJECT/ORG UUID
> 调用 DJI 司空2 OpenAPI（`es-flight-api-cn.djigate.com`），所有关键假设已通过实际返回数据确认。

---

## 1. MinIO 路径 ↔ DJI 司空2 数据映射（100% 已验证）

### 1.1 完整路径解构

```
MinIO ObjectKey:
  flydata/fh_sync/8566c7de-05ac-41ee-b260-1614848ed037
                  /826ccf49-8430-4037-a505-cd43b868d4d6
                  /media
                  /dbc84414-a612-4637-9964-ea4e8c3ca2a6
                  /DJI_20260826130711_0001_V.jpeg
```

| 路径段 | 示例值 | DJI 司空2 含义 | 验证方法 |
|---|---|---|---|
| `fh_sync/` | (固定前缀) | flight_sync，司空文件同步根目录 | 约定俗成 |
| **UUID1** | `8566c7de-05ac-41ee-b260-1614848ed037` | **organization_uuid**（组织ID） | 配置 `default-organization-uuid` 完全一致 ✅ |
| **UUID2** | `826ccf49-8430-4037-a505-cd43b868d4d6` | **project_uuid**（项目ID = "土方计算项目"） | 配置 `default-project-uuid` 完全一致 ✅ |
| `media/` | (固定前缀) | 媒体资源目录 | API文档约定 |
| **FLIGHT_UUID** | `dbc84414-a612-4637-9964-ea4e8c3ca2a6` | **flight_task_id / task_uuid**（飞行任务UUID） | `/flight-task/{FLIGHT_UUID}` 返回成功 ✅ |
| 文件名 | `DJI_20260826130711_0001_V.jpeg` | `DJI_{拍摄时间}_{序号}_V.{扩展名}`  (V=正射垂直影像) | `/flight-task/{TASK_UUID}/media` 15张文件名前缀完全一致 ✅ |

### 1.2 FLIGHT_UUID → 飞机SN / 元数据 获取方式（已验证）

**关键 API：**
```
GET /openapi/v2.0/flight-task/detail
    ?workspace_id = {PROJECT_UUID}   (826ccf49-...)
    &task_uuid    = {FLIGHT_UUID}    (dbc84414-...)
Headers:
    X-User-Token        : (同航线同步的 JWT)
    X-Organization-Uuid : {ORG_UUID} (8566c7de-...)
    X-Project-Uuid      : {PROJECT_UUID}
    X-Language          : zh
```

**返回可用字段（真实数据示例）：**
```json
{
  "flight_pieces": [{
    "flight_piece_id":     "dbc84414-a612-4637-9964-ea4e8c3ca2a6",  // 同 FLIGHT_UUID
    "drone_sn":            "1581F8HHX25CL00A06BL",                   // ✅ 飞机SN
    "drone_info": {
        "device_sn":       "1581F8HHX25CL00A06BL",
        "device_name":     "Matrice 4D",                              // ✅ 设备名称
        "device_model":    "0-100-0",                                 // ✅ 设备型号
        "device_type":     100
    },
    "task_name":           "航线任务-测试航线-2026-8-26",            // ✅ 任务名
    "status":              "success",                                 // ✅ 执行状态
    "take_off_airport_sn": "8UUXN5U00A09RH",                          // ✅ 起飞机场SN
    "begin_at":            "2026-08-26T05:06:07.869Z",                // ✅ 起飞时间
    "completed_at":        "2026-08-26T05:10:21.281Z"                 // ✅ 完成时间
  }],
  "folder_id": 325488492,
  "flight_task_id": "dbc84414-..."
}
```

**配套验证 API：**
- `GET /openapi/v2.0/flight-task/{TASK_UUID}` → 返回 `folder_info.uploaded_file_count` 可校验文件是否已全部同步完毕（15/15=完成才处理）。
- `GET /openapi/v2.0/flight-task/{TASK_UUID}/media` → 返回 media list，可和 MinIO 实际文件数量/名称双重校验，避免半同步半处理。
- `GET /openapi/v2.0/flight-task/{TASK_UUID}/track` → 返回 `wayline_uuid`，可与本地航线库关联（可选）。

### 1.3 MinIO 连接配置（已连通性测试通过）

| 项 | 值 | 验证 |
|---|---|---|
| Endpoint | `http://192.168.2.200:9000` | HTTP响应正常（需鉴权=403） ✅ |
| Access Key | `admin` | - |
| Secret Key | `Admin@123456` | - |
| Bucket | `flydata` | 桶存在（非404） ✅ |
| 网络延迟 | 16ms | 稳定 ✅ |

---

## 2. 核心问题与决策（与v1一致，保留）

### 2.1 轮询（Polling）vs Webhook

**选择轮询。原因：**
1. **零服务端配置**：无需在 MinIO 上配置 ARN/Webhook（可能受权限/版本限制）。
2. **高可靠性**：后端重启不丢任务；天然支持幂等。
3. **场景适配**：飞行架次少（每天数次），30-60秒级延迟完全可接受。
4. **批量友好**：按 `FLIGHT_UUID` 目录 = 一个"架次"批量处理，与业务语义匹配。

---

## 3. 详细设计方案（v2 已验证版）

### 3.1 整体流程图（已验证版）

```mermaid
graph TD
    classDef verified fill:#d4edda,stroke:#155724
    classDef planned fill:#cce5ff,stroke:#004085
    classDef reused fill:#fff3cd,stroke:#856404

    A[Spring Scheduled<br>每30秒触发]:::planned -->|扫描 fh_sync/| B[MinIO listObjects<br>递归列出所有 ObjectKey]:::planned

    B --> C[正则解析 ObjectKey<br>提取 {FLIGHT_UUID, filename}]:::verified
    C --> D[按 FLIGHT_UUID 分组<br>获得批次数组]:::planned

    D --> E{检查 Redis:<br>minio:flight:{FLIGHT_UUID}<br>状态?}:::planned
    E -- COMPLETED --> Z[跳过]
    E -- PROCESSING --> Z
    E -- 新任务 --> F[SET PROCESSING + 过期时间<br>分布式锁]:::planned

    F --> G[⭐调用 flight-task/detail<br>拿 drone_sn / 元数据]:::verified
    G --> H[⭐调用 flight-task/{uuid}<br>检查 uploaded_file_count == expected<br>确保全量已同步]:::verified
    H -- 仍在同步中 --> Y[释放 PROCESSING 锁<br>下一轮重试]
    H -- 全部完成 --> I[MinIO getObject<br>批量下载图片到<br>/tmp/minio/{FLIGHT_UUID}/]:::planned

    I --> J{并行触发(复用阶段1-2逻辑)}:::reused
    J -- 线程A 知眸GPU --> K[ZhimouTaskServiceImpl<br>createImageTask + reportImage<br>device_id=drone_sn ✅]:::reused
    J -- 线程B 自有模型CPU --> L[SelfModelDetectService.detectImage<br>device_sn=drone_sn ✅]:::reused

    K --> M[知眸异步回调入库<br>zhimou_image_detection<br>clean_status=0]:::reused
    L --> N[同步返回入库<br>self_model_image_detection<br>clean_status=0]:::reused

    M --> O[Redis 图片级状态跟踪<br>zhimou:arrived / self:arrived]:::planned
    N --> O
    O -- 两边都到齐 --> P[ImageCleanupService.cleanupSingleImage<br>clean_status 1正常/2误识别]:::reused

    P --> Q[Redis SET<br>minio:flight:{FLIGHT_UUID}=COMPLETED]:::planned
    Q --> R[清理 /tmp/minio/{FLIGHT_UUID}/ 临时文件<br>防磁盘占满]:::planned
```

### 3.2 关键实现点

#### A. 扫描与 ObjectKey 解析

```
prefix  = "fh_sync/"                 (MinIO listObjects 参数)
regex   = ^fh_sync/([^/]+)/([^/]+)/media/([^/]+)/(.+\.(?:jpe?g|png|JPG))$
groups:  1=ORG_UUID  2=PROJECT_UUID  3=TASK_UUID  4=filename
```

**校验点：**
- group(1) 必须等于 `DjiOpenApiProperties.defaultOrganizationUuid`
- group(2) 必须等于 `DjiOpenApiProperties.defaultProjectUuid`
- 不匹配 → 日志告警后跳过（防止其他组织/项目的文件错处理）

#### B. 批次状态管理（Redis）

| Key | 类型 | TTL | 说明 |
|---|---|---|---|
| `minio:flight:{FLIGHT_UUID}` | String (PROCESSING / COMPLETED) | PROCESSING=30min, COMPLETED=永久 | 批次级状态 |
| `minio:flight:img:{local_image_name}` | Hash {zhimou: 0/1, self: 0/1} | 7天 | 图片级状态，用于自动清洗触发 |

**状态流转：**
```
不存在 → PROCESSING (加锁，30分钟过期防死锁)
         ↓ 批次所有图片清洗完成
      COMPLETED (永久，避免重复处理；如需重跑可手动DEL)
```

#### C. "文件全量同步完成"校验（防半同步）

**必须在下载之前通过 API 校验：**
```java
// 1. 调 flight-task/{TASK_UUID} 拿 folder_info
JsonNode taskInfo = djiOpenApiClient.getFlightTaskDetail(projectUuid, taskUuid);
int expected = taskInfo.path("folder_info").path("expected_file_count").asInt();
int uploaded = taskInfo.path("folder_info").path("uploaded_file_count").asInt();
if (expected == 0 || uploaded < expected) {
    // 公网→内网MinIO仍在同步中，放弃本轮处理
    log.info("FLIGHT_UUID={} 仍在同步中 ({}/{}), 延后", taskUuid, uploaded, expected);
    // 回滚 PROCESSING 锁
    redisTemplate.delete("minio:flight:" + taskUuid);
    return;
}

// 2. 双保险：调 media 接口数量与 MinIO 实际文件数比对
List<MinioItem> minioImgs = listImagesInFlight(taskUuid);
JsonNode mediaList = djiOpenApiClient.getFlightTaskMedia(taskUuid);  // 需新增方法
if (mediaList.size() != minioImgs.size()) {
    log.warn("MinIO文件数与API不一致, 延后");
    redisTemplate.delete(...);
    return;
}
```

#### D. 图片下载 + local_image_name 生成

```java
// 下载目录: {minio.temp-dir}/{FLIGHT_UUID}/{filename}
// 默认 temp-dir: java.io.tmpdir + "/minio-images"
// 若不配置 temp-dir, 可用已有的 zhimou.image.save-root-dir = H:/traeproject/zhoudao/zhimou-images

// local_image_name = {TASK_UUID}/{filename}   ← 含飞行架次，保证跨架次唯一
String localImageName = taskUuid + "/" + filename;
// 例: dbc84414-a612-4637-9964-ea4e8c3ca2a6/DJI_20260826130711_0001_V.jpeg
```

**注意**：API `/media` 返回的 `name` 没有 `.jpeg` 扩展名，MinIO 中带扩展名，比对时统一去掉扩展名即可。

#### E. 并行识别（复用阶段1-2逻辑）

**抽取现有 `ImageRecognitionTestController.runTest` 为 Service 方法**，统一入口：

```java
// 在 MinioPollingService 中
CompletableFuture<Void> zhimouFuture = CompletableFuture.runAsync(() -> {
    // 完全复用 ZhimouTaskServiceImpl 的 createImageTask + reportImage 单张循环
    // device_id = drone_sn (从 detail 接口拿到的真实值 ✅)
}, imageTaskExecutor);

CompletableFuture<Void> selfFuture = CompletableFuture.runAsync(() -> {
    // 完全复用 SelfModelDetectService.detectImage
    // device_sn = drone_sn ✅
}, selfModelExecutor);

CompletableFuture.allOf(zhimouFuture, selfFuture)
    // 自有模型同步完成立即写 Redis img 状态 self=1
    // 知眸由回调处写 zhimou=1 + 触发清洗检查
```

**SN 处理**：v1 中"暂时用默认SN"的方案已废弃。现在优先用 detail 接口返回的 `drone_sn`，仅当该字段为空时 fallback 到配置的默认SN（兜底）。

#### F. 自动清洗触发（图片级 Redis 状态）

**两个写入点：**
1. 自有模型 `SelfModelDetectService` 入库后 → `HSET minio:flight:img:{name} self 1`
2. 知眸回调 `ZhimouController` / `ZhimouTaskCallbackService` 入库后 → `HSET minio:flight:img:{name} zhimou 1`

**每次 SET 后触发检查：**
```java
Map entries = redis.opsForHash().entries(key);
if (entries.containsKey("zhimou") && entries.containsKey("self")) {
    // 两边结果都到齐 → 立即清洗
    imageCleanupService.cleanupSingleImage(localImageName);
    // 清洗完成, 从图片级集合中移除 (或设置 TTL)
    redis.delete(key);

    // 如果这是批次最后一张未清洗的图片 → 批次标记 COMPLETED + 删临时目录
    // 实现: 计数器/扫描 minio:flight:img:{TASK_UUID}* 若都无则收尾
}
```

---

## 4. 代码结构规划（不写代码，仅规划）

### 4.1 新增文件

| 文件 | 作用 |
|---|---|
| `config/MinioProperties.java` | `@ConfigurationProperties(prefix="minio")` 读取 endpoint/accessKey/secretKey/bucket/temp-dir/poll-interval-ms |
| `config/MinioConfig.java` | `@Bean MinioClient minioClient()` + 健康检查 Bean |
| `service/MinioPollingService.java` | **核心**：`@Scheduled scanAndProcess()`、解析、分组、批次调度、下载、触发并行、收尾清理 |
| `controller/MinioController.java`（可选） | `POST /api/minio/notify` 兼容未来 Webhook 模式，或人工重跑单批次接口 `POST /api/minio/retry/{task_uuid}` |
| `resources/application-*.yml` 片段 | 新增 `minio:` 配置段 |
| `.../schema.sql`（可选） | 若批次状态要持久化到 DB（目前 Redis 足够，暂不加） |

### 4.2 复用/修改现有文件

| 文件 | 修改内容 |
|---|---|
| `DjiOpenApiClient.java` | 新增 3 个方法：`getFlightTaskDetail(workspaceId, taskUuid)`、`getFlightTaskInfo(taskUuid)`、`getFlightTaskMedia(taskUuid)`（复用现有 get/post 模板） |
| `SelfModelDetectService.java` | 入库操作后追加 Redis Hash `minio:flight:img:{name} self=1` + 触发清洗检查（新功能点） |
| `ZhimouTaskCallbackService.java`（或 ZhimouController 的 callback 处） | 入库后追加 Redis Hash `minio:flight:img:{name} zhimou=1` + 触发清洗检查 |
| `ImageRecognitionTestController.java` | 将手动测试的并行逻辑抽取到一个 `processImageBatch(List<ImageFile>, String droneSn)` Service 方法，供 MinioPollingService 复用 |

### 4.3 新增配置 (application-*.yml)

```yaml
# 新增 minio 段
minio:
  endpoint: http://192.168.2.200:9000
  access-key: admin
  secret-key: Admin@123456
  bucket: flydata
  # 同步过来的图片路径前缀（正则要求）
  scan-prefix: fh_sync/
  # 轮询间隔（毫秒）
  poll-interval-ms: 30000
  # 图片临时本地落盘目录（不填则用 zhimou.image.save-root-dir + /minio）
  temp-dir: H:/traeproject/zhoudao/zhimou-minio-temp
  # 是否在处理完成后删除临时文件（true 推荐）
  cleanup-temp-after: true
  # 处理前是否校验 API 中的 uploaded == expected（true 推荐，防半同步）
  validate-upload-count-before-process: true
```

---

## 5. 风险与边界

| 风险 | 缓解方案 |
|---|---|
| 下载中程序崩溃/重启 → 临时文件残留 | 下次启动后扫描 temp-dir 中 > PROCESSING TTL 的目录，整体删除 |
| PROCESSING 死锁（加锁后程序退出） | 设 TTL = 30 分钟，超过自动失效，下一轮扫描可重新接管 |
| 公网→内网 MinIO 仍在同步（uploaded < expected） | 通过 flight-task/{uuid} 的 `folder_info` 校验，未达 expected 则跳过本轮，下一轮重试 |
| 知眸回调失败/丢失，导致批次永远 COMPLETED 不了 | 增加兜底定时任务：扫描"PROCESSING 超过 2 小时"的批次，标记 PARTIAL_COMPLETE 并告警；或提供手动重试 Web 接口 |
| 自有模型接口超时/宕机 | 复用阶段 2 已有超时/重试逻辑；失败则该批次单张图片标记异常，人工可重试 |
| 图片重复上报（Redis 状态丢失） | DB 层 `local_image_name` 建唯一索引或插入前查重，清洗只按最新记录进行 |
| MinIO 不可用（网络问题） | 定时任务 try-catch，失败告警但不中断其他任务；本轮放弃下一轮再试 |

---

## 6. 与原有"手动测试入口"的关系

| 维度 | 手动测试入口（阶段1-2） | MinIO轮询入口（阶段3） |
|---|---|---|
| 触发方式 | 前端按钮 `POST /api/image-test/run` | Spring `@Scheduled` 每30秒 |
| 图片来源 | 本地扫描目录 imageFiles[] | MinIO getObject 下载 |
| SN 来源 | 前端传入（或默认） | **flight-task/detail API 实时获取** |
| 清洗触发 | 前端收到知眸回调后手动调 | Redis 状态双到齐后自动触发 |
| 批次状态 | 无 | Redis PROCESSING / COMPLETED |
| 开关 | 受 `image_task` 开关控制 | **同样受 `image_task` 开关控制**（关闭时扫描但跳过） |

**结论：两个入口完全共存，互不干扰，共享全部识别 + 清洗 + 入库 + 告警逻辑。**

---

## 7. 文档版本记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1 | 2026-08-26 | 初稿，假设 MinIO 路径映射关系，SN 暂用默认值 |
| **v2（当前）** | 2026-08-26 | ✅ **100% 验证路径映射**：UUID1=org、UUID2=project、FLIGHT_UUID=task_uuid<br>✅ **解决 SN 来源问题**：detail 接口实时返回 drone_sn，不再依赖默认值<br>✅ **新增上传完整性校验**：folder_info + media 数量防半同步<br>✅ 补充 MinIO 真实连接配置<br>✅ 补充图片级 Redis 自动清洗触发机制<br>✅ 明确与手动入口的关系 |

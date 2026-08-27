package com.dji.sikong.openapi;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.dji.sikong.config.ZhimouProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 知眸AI视频分析平台 OpenAPI 客户端
 * 官方文档: https://dev.zhimouyun.com/
 * 鉴权方式: Authorization: Bearer {token}
 */
@Slf4j
@Component
public class ZhimouOpenApiClient {

    @Resource
    private ZhimouProperties properties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Token在Redis中的缓存key */
    private static final String TOKEN_CACHE_KEY = "zhimou:openapi:token";

    /** Token提前过期的缓冲秒数(避免刚好过期时调用失败) */
    private static final long TOKEN_EXPIRE_BUFFER_SEC = 60L;

    /** 最近一次调用的错误信息(用于上层Service回填到live_stream.zhimou_last_error, 避免"调用知眸接口返回空"掩盖真实错误) */
    private volatile String lastErrorDetail = null;
    public String getLastErrorDetail() { return lastErrorDetail; }
    public void clearLastErrorDetail() { lastErrorDetail = null; }

    // ================================================================
    // 基础HTTP工具方法
    // ================================================================

    private String buildUrl(String path) {
        String base = properties.getOpenapi().getBaseUrl();
        if (StrUtil.isBlank(base)) return path;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("Accept", "application/json");
        String token = getAccessToken();
        if (StrUtil.isNotBlank(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * 知眸标准返回格式: { code, msg, data }
     * @return data节点, 出错返回 null, 并打印日志
     */
    private JsonNode request(String method, String path, Object body, Map<String, Object> query) {
        String url = buildUrl(path);
        clearLastErrorDetail();
        try {
            HttpRequest req;
            switch (method.toUpperCase()) {
                case "POST":   req = HttpRequest.post(url);   break;
                case "PUT":    req = HttpRequest.put(url);    break;
                case "DELETE": req = HttpRequest.delete(url); break;
                case "GET":
                default:       req = HttpRequest.get(url);    break;
            }
            req.addHeaders(buildHeaders());
            if (query != null && !query.isEmpty()) {
                req.form(query);
            }
            if (body != null && !"GET".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) {
                String json = body instanceof String ? (String) body : JSONUtil.toJsonStr(body);
                req.body(json);
            }
            log.info("[知眸-API] {} url={}, query={}, body={}", method, url, query, body);
            System.out.println("[知眸-API-REQ] " + method + " " + url + " query=" + query + " body=" + (body != null && body.toString().length() > 300 ? body.toString().substring(0,300)+"..." : body));
            HttpResponse resp = req.execute();
            int httpStatus = resp.getStatus();
            String respBody = resp.body();
            log.info("[知眸-API] {} status={}, respBody={}", method, httpStatus, respBody);
            String shortBody = respBody != null && respBody.length() > 500 ? respBody.substring(0, 500) + "..." : respBody;
            System.out.println("[知眸-API-RESP] HTTP " + httpStatus + " -> " + shortBody);

            if (httpStatus == 404) {
                lastErrorDetail = "HTTP 404 接口不存在: " + path;
                System.out.println("[知眸-API] ⚠️  接口不存在(404): " + path + " - 该私有化版本可能未开放此OpenAPI接口");
                return null;
            }

            // ====== 先尝试解析响应体为JSON, 检查业务错误码(无论HTTP是否2xx都解析, 解决HTTP 401 code=10002不刷新token的bug) ======
            int code = -1;
            String msg = "";
            JsonNode root = null;
            if (respBody != null && !respBody.trim().isEmpty()) {
                String trimmed = respBody.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    try {
                        root = OBJECT_MAPPER.readTree(respBody);
                        if (root != null) {
                            code = root.has("code") ? root.get("code").asInt(-1) : -1;
                            msg = root.has("msg") ? root.get("msg").asText("") : "";
                        }
                    } catch (Exception ignore) {
                        // JSON解析失败, 后面按HTTP状态处理
                    }
                }
            }

            // ====== 核心修复: HTTP 401 Unauthorized 或 业务码 10002 都需要刷新token重试 ======
            boolean needRefreshToken = (httpStatus == 401) || (code == 10002);
            if (needRefreshToken) {
                log.warn("[知眸-API] Token无效(HTTP={}, code={}, msg={}), 清除Redis缓存后重新获取Token再试一次", httpStatus, code, msg);
                System.out.println("[知眸-API] 🔄 Token无效(HTTP " + httpStatus + ", code=" + code + ") → 清缓存 → 刷新Token → 重试1次");
                try {
                    stringRedisTemplate.delete(TOKEN_CACHE_KEY);
                } catch (Exception ignore) {
                    System.out.println("[知眸-API] ⚠️  删除Redis缓存失败(忽略), 继续刷新token");
                }
                return requestOnceMoreAfterRefreshToken(method, path, body, query);
            }

            // ====== 其他HTTP非2xx错误 ======
            if (httpStatus < 200 || httpStatus >= 300) {
                lastErrorDetail = "HTTP " + httpStatus + " 状态异常: " + (shortBody != null ? shortBody.replace("\n", " ") : "");
                System.out.println("[知眸-API] ⚠️  HTTP状态异常: " + httpStatus + ", path=" + path);
                return null;
            }

            // ====== 响应体空 ======
            if (respBody == null || respBody.trim().isEmpty()) {
                lastErrorDetail = "HTTP响应体为空";
                System.out.println("[知眸-API] ⚠️  响应体为空, path=" + path);
                return null;
            }

            // ====== 非JSON响应 ======
            if (root == null) {
                String trimmed = respBody.trim();
                lastErrorDetail = "响应非JSON(疑似Web页面): " + trimmed.substring(0, Math.min(100, trimmed.length()));
                System.out.println("[知眸-API] ⚠️  响应不是JSON格式(可能是Web页面), path=" + path + ", 响应前100字符: " + trimmed.substring(0, Math.min(100, trimmed.length())));
                return null;
            }

            // ====== 业务码非0 ======
            if (code != 0) {
                lastErrorDetail = "API返回 code=" + code + ", msg=" + msg;
                log.error("[知眸-API] 调用失败 code={}, msg={}, path={}", code, msg, path);
                System.out.println("[知眸-API] ❌ 失败! code=" + code + " msg=" + msg + " path=" + path);
                return null;
            }

            return root.has("data") ? root.get("data") : root;
        } catch (Exception e) {
            lastErrorDetail = "HTTP异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("[知眸-API] HTTP异常 method={}, url={}", method, url, e);
            System.out.println("[知眸-API] ❌ HTTP异常: " + method + " " + url + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private JsonNode requestOnceMoreAfterRefreshToken(String method, String path, Object body, Map<String, Object> query) {
        String url = buildUrl(path);
        try {
            HttpRequest req;
            switch (method.toUpperCase()) {
                case "POST":   req = HttpRequest.post(url);   break;
                case "PUT":    req = HttpRequest.put(url);    break;
                case "DELETE": req = HttpRequest.delete(url); break;
                case "GET":
                default:       req = HttpRequest.get(url);    break;
            }
            req.addHeaders(buildHeaders());
            if (query != null && !query.isEmpty()) req.form(query);
            if (body != null && !"GET".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) {
                req.body(body instanceof String ? (String) body : JSONUtil.toJsonStr(body));
            }
            HttpResponse resp = req.execute();
            JsonNode root = OBJECT_MAPPER.readTree(resp.body());
            int code = root.has("code") ? root.get("code").asInt(-1) : -1;
            if (code != 0) {
                log.error("[知眸-API] 重试后仍失败 code={}, msg={}", code, root.path("msg").asText());
                return null;
            }
            return root.has("data") ? root.get("data") : root;
        } catch (Exception e) {
            log.error("[知眸-API] Token刷新后重试异常", e);
            return null;
        }
    }

    // ================================================================
    // 1. Token 鉴权 (重点是缓存, 不要每次都获取)
    // ================================================================

    /** 手动清理Redis缓存的Token(调试/启动时调用 */
    public void clearTokenCache() {
        try { stringRedisTemplate.delete(TOKEN_CACHE_KEY); log.info("[知眸-Token] 已清理Redis缓存"); } catch (Exception ignore) {}
    }

    /**
     * 获取访问令牌.优先读Redis缓存,没有则调接口获取并写入缓存.
     * 知眸错误码: 10001=错误的appkey或appsecret, 10002=无效token
     */
    public String getAccessToken() {
        try {
            String cached = stringRedisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
            if (StrUtil.isNotBlank(cached)) {
                if (cached.startsWith("{") || cached.length() < 20) {
                    String reason = cached.length() < 20 ? "过短" : "JSON对象格式异常";
                    System.out.println("[知眸-Token] Redis缓存疑似非法(" + reason + "),清掉重取,长度=" + cached.length());
                    stringRedisTemplate.delete(TOKEN_CACHE_KEY);
                } else {
                    return cached;
                }
            }
        } catch (Exception e) {
            System.out.println("[知眸-Token] 读Redis缓存失败(忽略),直接调用接口取: " + e.getMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("appkey", properties.getOpenapi().getAppkey());
        body.put("appsecret", properties.getOpenapi().getAppsecret());

        String url = buildUrl("/api/token");
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json;charset=UTF-8");
            String json = JSONUtil.toJsonStr(body);
            log.info("[知眸-Token] POST {} body={}", url, json);
            System.out.println("[知眸-Token-REQ] POST " + url + " appkey=" + body.get("appkey"));
            HttpResponse resp = HttpRequest.post(url)
                    .addHeaders(headers)
                    .body(json)
                    .execute();
            String respBody = resp.body();
            log.info("[知眸-Token] resp status={}, body={}", resp.getStatus(), respBody);
            String shortBody = respBody != null && respBody.length() > 200 ? respBody.substring(0, 200) + "..." : respBody;
            System.out.println("[知眸-Token-RESP] HTTP " + resp.getStatus() + " -> " + shortBody);
            JsonNode root = OBJECT_MAPPER.readTree(respBody);
            int code = root.has("code") ? root.get("code").asInt(-1) : -1;
            if (code != 0) {
                log.error("[知眸-Token] 获取失败 code={}, msg={}", code, root.path("msg").asText());
                return null;
            }
            JsonNode data = root.has("data") ? root.get("data") : root;
            String token = data.path("token").asText(null);
            if (StrUtil.isBlank(token)) token = data.path("access_token").asText(null);
            if (StrUtil.isBlank(token)) {
                log.error("[知眸-Token] 返回data里未找到token字段: {}", data);
                return null;
            }
            long expiresIn = data.path("expires_in").asLong(7200L);
            long cacheSec = Math.max(30L, expiresIn - TOKEN_EXPIRE_BUFFER_SEC);
            try {
                stringRedisTemplate.opsForValue().set(TOKEN_CACHE_KEY, token, cacheSec, TimeUnit.SECONDS);
                log.info("[知眸-Token] 获取成功, 缓存{}秒", cacheSec);
            } catch (Exception e) {
                log.warn("[知眸-Token] 写Redis缓存失败", e);
            }
            return token;
        } catch (Exception e) {
            log.error("[知眸-Token] 获取Token异常", e);
            return null;
        }
    }

    // ================================================================
    // 2. 应用包 / 应用列表 (给前端选择 app_id, application_package_id)
    // ================================================================

    /**
     * 获取应用包列表 (知眸私有化部署接口路径: GET /api/zapk, "zapk"是知眸内部对应用包的缩写代号).
     * 返回格式: [{ zapk_id, name, apps:[{app_id, zapk_id, name, version, rule:{...}}], back_hide, created_at, updated_at }]
     */
    public JsonNode listApplicationPackages() {
        System.out.println("[知眸-应用包列表] 开始查询应用包列表, 使用知眸私有化部署专用路径 /api/zapk ...");
        JsonNode data = request("GET", "/api/zapk", null, null);
        if (data == null) {
            data = request("GET", "/api/app", null, null);
        }
        if (data == null) {
            data = request("GET", "/api/applications", null, null);
        }
        if (data == null) {
            data = request("GET", "/api/application-packages", null, null);
        }
        if (data == null) {
            data = request("GET", "/api/app-packages", null, null);
        }
        if (data == null) {
            System.out.println("========================================================================");
            System.out.println("[知眸-应用包列表] ⚠️  所有接口路径均返回空/404!");
            System.out.println("[知眸-应用包列表] 说明: 您当前知眸私有化部署版本未开放『应用包列表』OpenAPI接口。");
            System.out.println("[知眸-应用包列表] 解决办法: 请从知眸Web控制台(低空视觉AI开放平台)手动获取应用包ID(pkgId)和应用ID(appId),");
            System.out.println("[知眸-应用包列表]           然后配置到 application.yml 的以下字段:");
            System.out.println("[知眸-应用包列表]             zhimou.openapi.default-pkg-id: 应用包ID");
            System.out.println("[知眸-应用包列表]             zhimou.openapi.default-app-id: 应用ID");
            System.out.println("[知眸-应用包列表] 获取方式: 浏览器打开知眸Web控制台 -> 应用管理 -> 进入对应应用 -> 复制ID");
            System.out.println("========================================================================");
        } else {
            System.out.println("[知眸-应用包列表] ✅ 查询成功!");
            try {
                if (data.isArray()) {
                    System.out.println("[知眸-应用包列表]    应用包数量: " + data.size());
                    for (JsonNode pkg : data) {
                        String zapkId = pkg.path("zapk_id").asText("");
                        String name = pkg.path("name").asText("");
                        int appCount = pkg.path("apps").size();
                        System.out.println("[知眸-应用包列表]      - zapk_id=" + zapkId + ", name=" + name + ", 包含" + appCount + "个应用");
                    }
                }
                String shortStr = data.toString();
                System.out.println("[知眸-应用包列表]    原始数据预览: " + shortStr.substring(0, Math.min(200, shortStr.length())));
            } catch (Exception ignore) {}
        }
        return data;
    }

    /**
     * 获取指定应用包下的应用(规则)列表.
     * 注意: 知眸私有化部署没有单独的/apps接口, 而是在 /api/zapk 的返回数据中已包含每个包的apps数组.
     * 所以这里我们先调 /api/zapk 取完整数据, 再按 zapk_id 过滤出目标包的apps数组; 若不传zapkId则返回所有包的apps扁平化.
     */
    public JsonNode listAppsByPackage(String applicationPackageId) {
        System.out.println("[知眸-应用列表] 开始查询应用列表, applicationPackageId=" + applicationPackageId);
        try {
            // 方案1: 尝试单独的RESTful接口路径(知眸官方SaaS可能有这些路径)
            Map<String, Object> query = new HashMap<>();
            if (StrUtil.isNotBlank(applicationPackageId)) {
                query.put("application_package_id", applicationPackageId);
                query.put("zapk_id", applicationPackageId);
            }
            String[] tryPaths = new String[]{
                "/api/apps", "/api/app/list", "/api/applications/list",
                "/api/zapk/apps",
                StrUtil.isNotBlank(applicationPackageId) ? "/api/zapk/" + applicationPackageId + "/apps" : null
            };
            for (String path : tryPaths) {
                if (StrUtil.isBlank(path)) continue;
                JsonNode d = request("GET", path, null, query);
                if (d != null) {
                    System.out.println("[知眸-应用列表] ✅ 路径 " + path + " 查询成功!");
                    return d;
                }
            }

            // 方案2: 从 /api/zapk 整体结果中拆分应用列表 (私有化部署专用)
            System.out.println("[知眸-应用列表] 独立应用列表接口不可用, 改为从 /api/zapk 结果中提取apps字段...");
            JsonNode allZapk = request("GET", "/api/zapk", null, null);
            if (allZapk == null || !allZapk.isArray()) {
                System.out.println("========================================================================");
                System.out.println("[知眸-应用列表] ⚠️  /api/zapk 也取不到数据, 无法获取应用列表!");
                System.out.println("[知眸-应用列表] 说明: 您当前知眸私有化部署版本未开放OpenAPI接口。");
                System.out.println("[知眸-应用列表] 解决办法: 请从知眸Web控制台(低空视觉AI开放平台)手动获取应用ID(appId),");
                System.out.println("[知眸-应用列表]           然后配置到 application.yml 的 zhimou.openapi.default-app-id 字段");
                System.out.println("========================================================================");
                return null;
            }
            // 从/zapk数组中提取每个包的apps, 合并返回
            com.fasterxml.jackson.databind.node.ArrayNode resultApps = OBJECT_MAPPER.createArrayNode();
            for (JsonNode pkg : allZapk) {
                String zapkId = pkg.path("zapk_id").asText("");
                if (StrUtil.isNotBlank(applicationPackageId) && !applicationPackageId.equals(zapkId)) {
                    continue;
                }
                JsonNode apps = pkg.path("apps");
                if (apps.isArray()) {
                    for (JsonNode app : apps) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) app).put("zapk_id", zapkId);
                        ((com.fasterxml.jackson.databind.node.ObjectNode) app).put("zapk_name", pkg.path("name").asText(""));
                        resultApps.add(app);
                    }
                }
            }
            System.out.println("[知眸-应用列表] ✅ 从 /api/zapk 提取成功! 共" + resultApps.size() + "个应用" + (StrUtil.isNotBlank(applicationPackageId) ? " (zapkId=" + applicationPackageId + ")" : " (全部包)"));
            for (JsonNode app : resultApps) {
                System.out.println("[知眸-应用列表]    - app_id=" + app.path("app_id").asText("") + ", name=" + app.path("name").asText("") + ", zapk=" + app.path("zapk_name").asText(""));
            }
            return resultApps;
        } catch (Exception e) {
            System.out.println("[知眸-应用列表] ❌ 查询异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("[知眸-应用列表] 查询异常", e);
            return null;
        }
    }

    // ================================================================
    // 3. 视频/图片 任务管理
    // ================================================================

    /**
     * 创建分析任务 (POST /api/task)
     *
     * @param bodyMap 完整请求体:
     *   - mode: video(默认) / image
     *   - app_id: 必填! 前端选的应用ID
     *   - input: 视频流地址(如SRS的RTMP地址)
     *   - output: AI叠加标注的输出流地址(可选)
     *   - osd: 显示配置(可选)
     *   - report.httpurl: 我们SpringBoot的回调地址
     *   - device: 设备号(飞机SN)
     *   - business_id: 我们的业务ID(live_stream.id)
     *   - dji.device_sn: 大疆飞机SN
     */
    public JsonNode createTask(Map<String, Object> bodyMap) {
        if (bodyMap == null) bodyMap = new HashMap<>();
        // report.httpurl 兜底自动填充
        if (!bodyMap.containsKey("report") && StrUtil.isNotBlank(properties.getOpenapi().getCallbackUrl())) {
            Map<String, Object> report = new HashMap<>();
            report.put("httpurl", properties.getOpenapi().getCallbackUrl());
            bodyMap.put("report", report);
        }
        return request("POST", "/api/task", bodyMap, null);
    }

    /**
     * 获取任务列表 (GET /api/task)
     */
    public JsonNode listTasks() {
        return request("GET", "/api/task", null, null);
    }

    /**
     * 获取单个任务详情 (GET /api/task/{taskId})
     */
    public JsonNode getTaskDetail(String taskId) {
        if (StrUtil.isBlank(taskId)) return null;
        return request("GET", "/api/task/" + taskId, null, null);
    }

    /**
     * 关闭分析任务 (DELETE /api/task/{taskId})
     * 飞机降落时必须调用, 避免知眸浪费GPU算力
     */
    public JsonNode deleteTask(String taskId) {
        if (StrUtil.isBlank(taskId)) return null;
        return request("DELETE", "/api/task/" + taskId, null, null);
    }

    /**
     * 配置分析任务 (PUT /api/task/{taskId})
     * 动态修改OSD展示配置和画面过滤区域, 无需重启任务
     */
    public JsonNode updateTask(String taskId, Map<String, Object> bodyMap) {
        if (StrUtil.isBlank(taskId)) return null;
        return request("PUT", "/api/task/" + taskId, bodyMap, null);
    }

    /**
     * 获取指定应用AI视频 (POST /api/task/{taskId}/ailive)
     * 用于获取AI分析后的视频流，并可推流到指定的RTMP地址
     *
     * @param taskId 任务ID
     * @param rtmp 推流地址(可选，为空时返回可播放的视频流地址)
     * @param bitrate 码率(可选)
     * @param appIds 应用ID列表(必需)
     */
    public JsonNode getAiVideoLive(String taskId, String rtmp, Integer bitrate, java.util.List<String> appIds) {
        if (StrUtil.isBlank(taskId)) return null;
        Map<String, Object> body = new HashMap<>();
        if (StrUtil.isNotBlank(rtmp)) body.put("rtmp", rtmp);
        if (bitrate != null) body.put("bitrate", bitrate);
        if (appIds != null && !appIds.isEmpty()) body.put("app_ids", appIds);
        return request("POST", "/api/task/" + taskId + "/ailive", body, null);
    }

    // ================================================================
    // 4. 任务运行期数据上报
    // ================================================================

    /**
     * 上报飞行位置/姿态 (POST /api/task/{taskId}/flight)
     * 飞行中定时调这个, 让知眸把识别结果和GPS位置关联起来.
     *
     * @param flightMap: { latitude, longitude, altitude, height,
     *      attitude_pitch, attitude_roll, attitude_yaw,
     *      gimbal_pitch, gimbal_roll, gimbal_yaw,
     *      horizontal_speed, vertical_speed, zoom_factor, timestamp }
     */
    public JsonNode reportFlight(String taskId, Map<String, Object> flightMap) {
        if (StrUtil.isBlank(taskId) || flightMap == null || flightMap.isEmpty()) return null;
        if (!flightMap.containsKey("timestamp")) {
            flightMap.put("timestamp", System.currentTimeMillis());
        }
        return request("POST", "/api/task/" + taskId + "/flight", flightMap, null);
    }

    /**
     * 上报图片给图片分析任务 (POST /api/task/{taskId}/image)
     * mode=image 时使用; 视频分析任务不用.
     *
     * 接口文档: https://openapi.zhimouyun.com/470173793e0
     * - Path: /api/task/{task_id}/image
     * - Query: image_id (integer, 可选, 回调里原样返回为 data.frame.id)
     * - Query: device_id (string, 可选, 会更新任务开启时的device)
     * - Query: enhance (integer, 默认0, 1=开启图片增强识别)
     * - Body: application/octet-stream (二进制图片)
     * - 限制: 每任务最小上报间隔1秒
     *
     * @param imageId 图片编号(调用方指定, 回调里原样返回, 方便关联)
     * @param device  设备号(飞机SN)
     * @param imageBytes 图片二进制
     */
    public JsonNode reportImage(String taskId, long imageId, String device, byte[] imageBytes) {
        if (StrUtil.isBlank(taskId) || imageBytes == null) return null;
        // 按 API 文档, query 参数拼接到 URL, Body 为二进制流
        StringBuilder url = new StringBuilder(buildUrl("/api/task/" + taskId + "/image"));
        url.append("?image_id=").append(imageId);
        if (StrUtil.isNotBlank(device)) url.append("&device_id=").append(device);
        // enhance 默认0, 不传
        try {
            Map<String, String> headers = buildHeaders();
            headers.put("Content-Type", "application/octet-stream");
            String urlStr = url.toString();
            log.info("[知眸-上报图片] POST url={}, bodySize={}bytes, timeout=10s", urlStr, imageBytes.length);
            HttpResponse resp = HttpRequest.post(urlStr)
                    .addHeaders(headers)
                    .body(imageBytes)
                    .timeout(10000)
                    .execute();
            String respBody = resp.body();
            log.info("[知眸-上报图片] resp status={}, body={}", resp.getStatus(), respBody);
            JsonNode root = OBJECT_MAPPER.readTree(respBody);
            int code = root.has("code") ? root.get("code").asInt(-1) : -1;
            if (code != 0) {
                log.error("[知眸-上报图片] 失败 code={}, msg={}", code, root.path("msg").asText());
                return null;
            }
            return root.has("data") ? root.get("data") : root;
        } catch (Exception e) {
            log.error("[知眸-上报图片] 异常 taskId={}, imageId={}, error={}", taskId, imageId, e.getMessage());
            return null;
        }
    }
}

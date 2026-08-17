package com.dji.sikong.openapi;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class DjiOpenApiClient {

    @Resource
    private DjiOpenApiProperties properties;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Map<String, String> buildHeaders(String projectUuid) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("X-Language", properties.getLanguage());
        headers.put("X-Request-Id", java.util.UUID.randomUUID().toString().replace("-", ""));
        if (StrUtil.isNotBlank(properties.getUserToken())) {
            headers.put("X-User-Token", properties.getUserToken());
        }
        String orgUuid = properties.getDefaultOrganizationUuid();
        if (StrUtil.isNotBlank(orgUuid)) {
            headers.put("X-Organization-Uuid", orgUuid);
        }
        String actualProjectUuid = StrUtil.isNotBlank(projectUuid) ? projectUuid : properties.getDefaultProjectUuid();
        if (StrUtil.isNotBlank(actualProjectUuid)) {
            headers.put("X-Project-Uuid", actualProjectUuid);
        }
        return headers;
    }

    private String buildUrl(String path) {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return base + path;
    }

    public JsonNode get(String path, String projectUuid, Map<String, Object> query) {
        String url = buildUrl(path);
        try {
            HttpRequest req = HttpRequest.get(url).addHeaders(buildHeaders(projectUuid));
            if (query != null && !query.isEmpty()) {
                req.form(query);
            }
            log.debug("OpenAPI GET url={}, query={}", url, query);
            HttpResponse resp = req.execute();
            String body = resp.body();
            log.debug("OpenAPI GET resp status={}, body={}", resp.getStatus(), body);
            return OBJECT_MAPPER.readTree(body);
        } catch (Exception e) {
            log.error("OpenAPI GET 异常 url={}", url, e);
            return null;
        }
    }

    public JsonNode post(String path, String projectUuid, Object body) {
        String url = buildUrl(path);
        try {
            String json = body instanceof String ? (String) body : JSONUtil.toJsonStr(body);
            HttpRequest req = HttpRequest.post(url)
                    .addHeaders(buildHeaders(projectUuid))
                    .body(json);
            log.debug("OpenAPI POST url={}, body={}", url, json);
            HttpResponse resp = req.execute();
            String respBody = resp.body();
            log.debug("OpenAPI POST resp status={}, body={}", resp.getStatus(), respBody);
            return OBJECT_MAPPER.readTree(respBody);
        } catch (Exception e) {
            log.error("OpenAPI POST 异常 url={}", url, e);
            return null;
        }
    }

    public JsonNode put(String path, String projectUuid, Object body) {
        String url = buildUrl(path);
        try {
            String json = body instanceof String ? (String) body : JSONUtil.toJsonStr(body);
            HttpRequest req = HttpRequest.put(url)
                    .addHeaders(buildHeaders(projectUuid))
                    .body(json);
            log.debug("OpenAPI PUT url={}, body={}", url, json);
            HttpResponse resp = req.execute();
            String respBody = resp.body();
            log.debug("OpenAPI PUT resp status={}, body={}", resp.getStatus(), respBody);
            return OBJECT_MAPPER.readTree(respBody);
        } catch (Exception e) {
            log.error("OpenAPI PUT 异常 url={}", url, e);
            return null;
        }
    }

    /** 获取项目列表 */
    public JsonNode listProjects() {
        Map<String, Object> query = new HashMap<>();
        query.put("pageSize", properties.getDefaultPageSize());
        query.put("pageNum", 1);
        return get("/openapi/v2.0/project", null, query);
    }

    /** 获取项目设备列表 */
    public JsonNode listProjectDevices(String projectUuid) {
        String actual = StrUtil.isNotBlank(projectUuid) ? projectUuid : properties.getDefaultProjectUuid();
        Map<String, Object> query = new HashMap<>();
        if (StrUtil.isNotBlank(actual)) query.put("workspace_id", actual);
        query.put("pageSize", properties.getDefaultPageSize());
        query.put("pageNum", 1);
        return get("/openapi/v2.0/devices", actual, query);
    }

    /** 获取航线列表 */
    public JsonNode listWaylines(String projectUuid) {
        Map<String, Object> query = new HashMap<>();
        query.put("pageSize", properties.getDefaultPageSize());
        query.put("pageNum", 1);
        return get("/openapi/v2.0/wayline", projectUuid, query);
    }

    /** 获取航线详情 */
    public JsonNode getWaylineDetail(String projectUuid, String waylineId) {
        return get("/openapi/v2.0/wayline/" + waylineId, projectUuid, null);
    }

    /** 创建飞行任务 */
    public JsonNode createFlightTask(String projectUuid, Map<String, Object> body) {
        return post("/openapi/v2.0/flight-task", projectUuid, body);
    }

    /** 获取飞行任务列表 */
    public JsonNode listFlightTasks(String projectUuid) {
        Map<String, Object> query = new HashMap<>();
        query.put("pageSize", properties.getDefaultPageSize());
        query.put("pageNum", 1);
        return get("/openapi/v2.0/flight-task/list", projectUuid, query);
    }

    /** 获取飞行任务详情 */
    public JsonNode getFlightTaskDetail(String projectUuid, String taskUuid) {
        Map<String, Object> query = new HashMap<>();
        query.put("workspace_id", StrUtil.isNotBlank(projectUuid) ? projectUuid : properties.getDefaultProjectUuid());
        return get("/openapi/v2.0/flight-task/" + taskUuid, projectUuid, query);
    }

    /** 获取最近飞行任务列表 */
    public JsonNode getRecentFlightTasks(String projectUuid, String sn) {
        Map<String, Object> query = new HashMap<>();
        query.put("sn[]", sn);
        String workspaceId = StrUtil.isNotBlank(projectUuid) ? projectUuid : properties.getDefaultProjectUuid();
        return get("/openapi/v2.0/workspaces/" + workspaceId + "/flight-tasks/recent", projectUuid, query);
    }

    /** 获取STS临时凭证 */
    public JsonNode getStsToken(String projectUuid) {
        Map<String, Object> body = new HashMap<>();
        body.put("bucket_type", 1);
        return post("/openapi/v2.0/project/sts-token", projectUuid, body);
    }

    /** 开启直播 */
    public JsonNode startLiveStream(String projectUuid, Map<String, Object> body) {
        return post("/openapi/v2.0/live-stream/start", projectUuid, body);
    }

    /** 创建码流转发器(旁路推流) */
    public JsonNode createStreamConverter(String projectUuid, Map<String, Object> body) {
        return post("/openapi/v2.0/live-stream/converter", projectUuid, body);
    }

    /** 更新码流转发器(旁路推流) */
    public JsonNode updateStreamConverter(String projectUuid, String converterId, Map<String, Object> body) {
        if (StrUtil.isBlank(converterId)) return null;
        return put("/openapi/v2.0/live-stream/converter/" + converterId, projectUuid, body);
    }

    /** 获取码流转发器列表 */
    public JsonNode listStreamConverters(String projectUuid) {
        Map<String, Object> query = new HashMap<>();
        query.put("pageSize", properties.getDefaultPageSize());
        query.put("pageNum", 1);
        return get("/openapi/v2.0/stream-converters", projectUuid, query);
    }

    /** 停止码流转发器(旁路推流关闭) - PUT /converter/{cid} auto_push_stream=false */
    public JsonNode stopStreamConverter(String projectUuid, String converterId) {
        if (StrUtil.isBlank(converterId)) return null;
        Map<String, Object> body = new HashMap<>();
        body.put("auto_push_stream", false);
        return put("/openapi/v2.0/live-stream/converter/" + converterId, projectUuid, body);
    }

    /** 关闭直播接口（非旁路） */
    public JsonNode stopLiveStream(String projectUuid, String streamId) {
        if (StrUtil.isBlank(streamId)) return null;
        Map<String, Object> body = new HashMap<>();
        body.put("id", streamId);
        return post("/openapi/v2.0/live-stream/stop", projectUuid, body);
    }
}

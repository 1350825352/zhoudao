package com.dji.sikong.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.entity.OrganizationProject;
import com.dji.sikong.mapper.OrganizationProjectMapper;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.service.OrganizationProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrganizationProjectServiceImpl
        extends ServiceImpl<OrganizationProjectMapper, OrganizationProject>
        implements OrganizationProjectService {

    @Resource
    private OrganizationProjectMapper mapper;

    @Resource
    private DjiOpenApiClient openApiClient;

    @Override
    public List<OrganizationProject> syncFromOpenApi() {
        List<OrganizationProject> result = new ArrayList<>();
        JsonNode resp = openApiClient.listProjects();
        if (resp == null) return result;
        JsonNode list = null;
        if (resp.has("data") && resp.get("data").has("list")) list = resp.get("data").get("list");
        else if (resp.has("list")) list = resp.get("list");
        else if (resp.isArray()) list = resp;

        if (list == null || !list.isArray()) return result;

        // 收集司空2返回的所有project_uuid，用于最后删除不在列表中的旧数据
        java.util.Set<String> fetchedUuids = new java.util.HashSet<>();
        for (JsonNode node : list) {
            String puuid = node.has("project_uuid") ? node.get("project_uuid").asText()
                    : (node.has("uuid") ? node.get("uuid").asText() : null);
            if (puuid == null) continue;
            fetchedUuids.add(puuid);
            OrganizationProject exist = mapper.selectOne(
                    new LambdaQueryWrapper<OrganizationProject>().eq(OrganizationProject::getProjectUuid, puuid));
            OrganizationProject op = exist == null ? new OrganizationProject() : exist;
            op.setProjectUuid(puuid);
            if (node.has("org_uuid")) op.setOrgUuid(node.get("org_uuid").asText());
            if (node.has("organization_uuid")) op.setOrgUuid(node.get("organization_uuid").asText());
            if (node.has("project_name")) op.setProjectName(node.get("project_name").asText());
            else if (node.has("name")) op.setProjectName(node.get("name").asText());
            if (node.has("org_name")) op.setOrgName(node.get("org_name").asText());
            if (node.has("organization_name")) op.setOrgName(node.get("organization_name").asText());
            if (op.getOrgName() == null || "".equals(op.getOrgName())) {
                // 司空2 OpenAPI返回不带org_name时给默认值,避免数据库字段无默认值报错
                op.setOrgName(op.getOrgUuid() == null ? "默认组织" : "组织-" + op.getOrgUuid().replaceAll("-.*$", ""));
            }
            if (node.has("introduction")) op.setIntroduction(node.get("introduction").asText());
            if (node.has("center_latitude")) {
                try { op.setCenterLatitude(new BigDecimal(node.get("center_latitude").asText())); } catch (Exception ignore) {}
            }
            if (node.has("center_longitude")) {
                try { op.setCenterLongitude(new BigDecimal(node.get("center_longitude").asText())); } catch (Exception ignore) {}
            }
            // 司空2项目中心点在 project_work_center_point.{latitude,longitude} 子节点
            if (op.getCenterLatitude() == null || op.getCenterLongitude() == null) {
                JsonNode center = node.has("project_work_center_point") ? node.get("project_work_center_point") : null;
                if (center != null) {
                    if (center.has("latitude") && op.getCenterLatitude() == null) {
                        try { op.setCenterLatitude(new BigDecimal(center.get("latitude").asText())); } catch (Exception ignore) {}
                    }
                    if (center.has("longitude") && op.getCenterLongitude() == null) {
                        try { op.setCenterLongitude(new BigDecimal(center.get("longitude").asText())); } catch (Exception ignore) {}
                    }
                }
            }
            op.setLastSyncTime(LocalDateTime.now());
            if (exist == null) {
                // 先物理删除可能已逻辑删除但唯一索引仍存在的旧记录
                mapper.physicalDeleteByProjectUuid(puuid);
                mapper.insert(op);
            } else {
                mapper.updateById(op);
            }
            result.add(op);
        }

        // 同步成功后，删除数据库中司空2未返回的旧项目数据（清理开发期间残留的脏数据）
        if (!fetchedUuids.isEmpty()) {
            try {
                mapper.delete(new LambdaQueryWrapper<OrganizationProject>()
                        .notIn(OrganizationProject::getProjectUuid, fetchedUuids));
            } catch (Exception e) {
                // 删除旧数据失败不影响同步结果，只记录日志
                System.out.println("[项目同步] 删除旧项目数据失败(不影响同步): " + e.getMessage());
            }
        }
        return result;
    }
}

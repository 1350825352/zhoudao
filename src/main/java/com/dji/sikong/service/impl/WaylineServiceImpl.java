package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.dji.sikong.entity.OrganizationProject;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.mapper.WaylineMapper;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.service.OrganizationProjectService;
import com.dji.sikong.service.WaylineService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WaylineServiceImpl extends ServiceImpl<WaylineMapper, Wayline> implements WaylineService {

    @Resource
    private WaylineMapper waylineMapper;

    @Resource
    private DjiOpenApiClient openApiClient;

    @Resource
    private OrganizationProjectService projectService;

    @Resource
    private DjiOpenApiProperties openApiProperties;

    @Override
    public List<Wayline> syncFromOpenApi(String projectUuid) {
        List<Wayline> result = new ArrayList<>();
        List<String> projectUuids = new ArrayList<>();

        // 优先级：1. 调用方传入  2. 配置文件中的default-project-uuid  3. 遍历organization_project表
        if (StrUtil.isNotBlank(projectUuid)) {
            projectUuids.add(projectUuid);
        } else if (StrUtil.isNotBlank(openApiProperties.getDefaultProjectUuid())) {
            projectUuids.add(openApiProperties.getDefaultProjectUuid());
        } else {
            List<OrganizationProject> projects = projectService.list(
                    new LambdaQueryWrapper<OrganizationProject>().eq(OrganizationProject::getDeleted, 0));
            for (OrganizationProject p : projects) {
                projectUuids.add(p.getProjectUuid());
            }
        }

        List<String> allOpenApiUuids = new ArrayList<>();
        
        for (String puuid : projectUuids) {
            JsonNode resp = openApiClient.listWaylines(puuid);
            if (resp == null) continue;
            JsonNode list;
            if (resp.has("data") && resp.get("data").has("list")) {
                list = resp.get("data").get("list");
            } else if (resp.has("list")) {
                list = resp.get("list");
            } else {
                list = resp.isArray() ? resp : null;
            }
            if (list == null || !list.isArray()) continue;

            String projectName = null;
            OrganizationProject project = projectService.getOne(
                    new LambdaQueryWrapper<OrganizationProject>().eq(OrganizationProject::getProjectUuid, puuid));
            if (project != null) projectName = project.getProjectName();

            for (JsonNode node : list) {
                String wuuid = node.has("wayline_uuid") ? node.get("wayline_uuid").asText()
                        : (node.has("uuid") ? node.get("uuid").asText()
                        : (node.has("id") ? node.get("id").asText() : null));
                if (StrUtil.isBlank(wuuid)) continue;
                
                allOpenApiUuids.add(wuuid);

                Wayline exist = waylineMapper.selectOne(
                        new LambdaQueryWrapper<Wayline>().eq(Wayline::getWaylineUuid, wuuid));
                Wayline w = exist == null ? new Wayline() : exist;

                w.setWaylineUuid(wuuid);
                w.setProjectUuid(puuid);
                w.setProjectName(projectName);
                if (node.has("wayline_name")) w.setWaylineName(node.get("wayline_name").asText());
                else if (node.has("name")) w.setWaylineName(node.get("name").asText());
                if (node.has("wayline_type")) w.setWaylineType(node.get("wayline_type").asInt());
                if (node.has("template_type")) w.setTemplateType(node.get("template_type").asInt());
                if ((w.getTemplateType() == null || w.getTemplateType() == 0) && node.has("template_types") && node.get("template_types").isArray() && node.get("template_types").size() > 0) {
                    String t0 = node.get("template_types").get(0).asText();
                    try { w.setTemplateType(Integer.parseInt(t0)); } catch (NumberFormatException nfe) {
                        if ("waypoint".equals(t0)) w.setTemplateType(1);
                        else if ("mapping_2d".equals(t0)) w.setTemplateType(2);
                        else if ("mapping_3d".equals(t0)) w.setTemplateType(3);
                        else if ("mapping_strip".equals(t0)) w.setTemplateType(4);
                        else if ("mapping_points".equals(t0)) w.setTemplateType(5);
                        else if ("track".equals(t0)) w.setTemplateType(6);
                        else if ("panorama".equals(t0)) w.setTemplateType(7);
                        else w.setTemplateType(0);
                    }
                }
                if (node.has("wpml_object_key")) w.setWpmlObjectKey(node.get("wpml_object_key").asText());
                if (node.has("wpml_download_url")) w.setWpmlDownloadUrl(node.get("wpml_download_url").asText());
                if (node.has("total_distance")) w.setTotalDistance(toDecimal(node, "total_distance"));
                if (node.has("estimated_time")) w.setEstimatedTime(node.get("estimated_time").asInt());
                if (node.has("waypoint_count")) w.setWaypointCount(node.get("waypoint_count").asInt());
                if (node.has("height")) w.setHeight(toDecimal(node, "height"));
                if (node.has("speed")) w.setSpeed(toDecimal(node, "speed"));
                if (node.has("action_count")) w.setActionCount(node.get("action_count").asInt());
                if (node.has("size") && w.getTotalDistance() == null) {
                    try { w.setTotalDistance(new BigDecimal(node.get("size").asText())); } catch (Exception ignore) {}
                }
                if (node.has("device_model_key")) {
                    try { w.setRemark(node.get("device_model_key").asText()); } catch (Exception ignore) {}
                }

                w.setSyncSource(1);
                w.setLastSyncTime(LocalDateTime.now());
                try { w.setRawJson(node.toString()); } catch (Exception ignore) {}

                if (StrUtil.isBlank(w.getWpmlDownloadUrl()) && StrUtil.isNotBlank(wuuid)) {
                    try {
                        JsonNode detail = openApiClient.getWaylineDetail(puuid, wuuid);
                        if (detail != null && detail.has("data")) {
                            JsonNode data = detail.get("data");
                            if (data.has("download_url")) {
                                w.setWpmlDownloadUrl(data.get("download_url").asText());
                            } else if (data.has("wpml_download_url")) {
                                w.setWpmlDownloadUrl(data.get("wpml_download_url").asText());
                            }
                        }
                    } catch (Exception e) {
                    }
                }

                if (exist == null) waylineMapper.insert(w);
                else waylineMapper.updateById(w);
                result.add(w);
            }
        }
        
        List<Wayline> dbWaylines = waylineMapper.selectList(
                new LambdaQueryWrapper<Wayline>().eq(Wayline::getDeleted, 0).eq(Wayline::getSyncSource, 1));
        for (Wayline dbW : dbWaylines) {
            if (!allOpenApiUuids.contains(dbW.getWaylineUuid())) {
                waylineMapper.deleteById(dbW.getId());
            }
        }
        
        return result;
    }

    @Override
    public List<Wayline> listByProject(String projectUuid) {
        LambdaQueryWrapper<Wayline> wrapper = new LambdaQueryWrapper<Wayline>().eq(Wayline::getDeleted, 0);
        if (StrUtil.isNotBlank(projectUuid)) wrapper.eq(Wayline::getProjectUuid, projectUuid);
        wrapper.orderByDesc(Wayline::getUpdateTime);
        return waylineMapper.selectList(wrapper);
    }

    @Override
    public Wayline getByUuid(String waylineUuid) {
        if (StrUtil.isBlank(waylineUuid)) return null;
        return waylineMapper.selectOne(
                new LambdaQueryWrapper<Wayline>().eq(Wayline::getWaylineUuid, waylineUuid).eq(Wayline::getDeleted, 0));
    }

    private BigDecimal toDecimal(JsonNode node, String field) {
        if (!node.has(field)) return null;
        try { return new BigDecimal(node.get(field).asText()); }
        catch (Exception e) { return null; }
    }
}

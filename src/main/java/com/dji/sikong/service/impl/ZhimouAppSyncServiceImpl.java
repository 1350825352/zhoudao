package com.dji.sikong.service.impl;

import com.dji.sikong.entity.ZhimouApp;
import com.dji.sikong.entity.ZhimouAppPackage;
import com.dji.sikong.mapper.ZhimouAppMapper;
import com.dji.sikong.mapper.ZhimouAppPackageMapper;
import com.dji.sikong.openapi.ZhimouOpenApiClient;
import com.dji.sikong.service.ZhimouAppSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ZhimouAppSyncServiceImpl implements ZhimouAppSyncService {

    @Resource
    private ZhimouOpenApiClient zhimouOpenApiClient;

    @Resource
    private ZhimouAppPackageMapper zhimouAppPackageMapper;

    @Resource
    private ZhimouAppMapper zhimouAppMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> syncFromZhimou() {
        Map<String, Object> result = new HashMap<>();
        int packageCount = 0;
        int appCount = 0;
        int newPackageCount = 0;
        int newAppCount = 0;

        try {
            JsonNode packages = zhimouOpenApiClient.listApplicationPackages();
            
            if (packages == null || !packages.isArray()) {
                result.put("success", false);
                result.put("message", "从知眸获取应用包列表失败");
                return result;
            }

            LocalDateTime syncTime = LocalDateTime.now();

            zhimouAppMapper.deleteAll();
            zhimouAppPackageMapper.deleteAll();
            log.info("[知眸应用同步] 已清空数据库中所有应用包和应用数据");

            for (JsonNode pkgNode : packages) {
                String zapkId = pkgNode.path("zapk_id").asText("");
                if (zapkId.isEmpty()) {
                    continue;
                }

                ZhimouAppPackage pkg = new ZhimouAppPackage();
                pkg.setZapkId(zapkId);
                pkg.setName(pkgNode.path("name").asText(""));
                
                String createdAtStr = pkgNode.path("created_at").asText("");
                if (!createdAtStr.isEmpty()) {
                    try {
                        pkg.setCreatedAt(LocalDateTime.parse(createdAtStr.substring(0, 19)));
                    } catch (Exception e) {
                        try {
                            pkg.setCreatedAt(LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(Long.parseLong(createdAtStr)),
                                ZoneId.systemDefault()
                            ));
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                }

                String updatedAtStr = pkgNode.path("updated_at").asText("");
                if (!updatedAtStr.isEmpty()) {
                    try {
                        pkg.setUpdatedAt(LocalDateTime.parse(updatedAtStr.substring(0, 19)));
                    } catch (Exception e) {
                        // ignore
                    }
                }

                String downloadedAtStr = pkgNode.path("downloaded_at").asText("");
                if (!downloadedAtStr.isEmpty()) {
                    try {
                        pkg.setDownloadedAt(LocalDateTime.parse(downloadedAtStr.substring(0, 19)));
                    } catch (Exception e) {
                        // ignore
                    }
                }

                pkg.setDeleted(0);
                pkg.setSyncTime(syncTime);

                java.util.Map<String, Object> pkgQuery = new HashMap<>();
                pkgQuery.put("zapk_id", zapkId);
                pkgQuery.put("deleted", 0);
                ZhimouAppPackage existingPkg = zhimouAppPackageMapper.selectByMap(pkgQuery)
                    .stream().findFirst().orElse(null);

                if (existingPkg != null) {
                    pkg.setId(existingPkg.getId());
                    zhimouAppPackageMapper.updateById(pkg);
                } else {
                    zhimouAppPackageMapper.insert(pkg);
                    newPackageCount++;
                }
                packageCount++;

                JsonNode apps = pkgNode.path("apps");
                if (apps.isArray()) {
                    for (JsonNode appNode : apps) {
                        String appId = appNode.path("app_id").asText("");
                        if (appId.isEmpty()) {
                            continue;
                        }

                        ZhimouApp app = new ZhimouApp();
                        app.setAppId(appId);
                        app.setZapkId(zapkId);
                        app.setName(appNode.path("name").asText(""));
                        
                        String appUpdatedAtStr = appNode.path("updated_at").asText("");
                        if (!appUpdatedAtStr.isEmpty()) {
                            try {
                                app.setUpdatedAt(LocalDateTime.parse(appUpdatedAtStr.substring(0, 19)));
                            } catch (Exception e) {
                                // ignore
                            }
                        }

                        app.setLicenseExpire(appNode.path("license_expire").asLong(0));
                        app.setPerpetual(appNode.path("perpetual").asBoolean(false) ? 1 : 0);
                        app.setDeleted(0);
                        app.setSyncTime(syncTime);

                        java.util.Map<String, Object> appQuery = new HashMap<>();
                        appQuery.put("app_id", appId);
                        appQuery.put("deleted", 0);
                        ZhimouApp existingApp = zhimouAppMapper.selectByMap(appQuery)
                            .stream().findFirst().orElse(null);

                        if (existingApp != null) {
                            app.setId(existingApp.getId());
                            zhimouAppMapper.updateById(app);
                        } else {
                            zhimouAppMapper.insert(app);
                            newAppCount++;
                        }
                        appCount++;
                    }
                }
            }

            result.put("success", true);
            result.put("message", "同步成功");
            result.put("packageCount", packageCount);
            result.put("appCount", appCount);
            result.put("newPackageCount", newPackageCount);
            result.put("newAppCount", newAppCount);
            result.put("syncTime", syncTime.toString());

            log.info("[知眸应用同步] 同步完成: 应用包={}, 应用={}, 新增应用包={}, 新增应用={}", 
                packageCount, appCount, newPackageCount, newAppCount);

        } catch (Exception e) {
            log.error("[知眸应用同步] 同步失败", e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
            throw e;
        }

        return result;
    }

    @Override
    public int getPackageCount() {
        return Math.toIntExact(zhimouAppPackageMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ZhimouAppPackage>()
                .eq(ZhimouAppPackage::getDeleted, 0)
        ));
    }

    @Override
    public int getAppCount() {
        return Math.toIntExact(zhimouAppMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ZhimouApp>()
                .eq(ZhimouApp::getDeleted, 0)
        ));
    }
}
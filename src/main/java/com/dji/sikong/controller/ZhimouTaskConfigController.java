package com.dji.sikong.controller;

import com.dji.sikong.common.R;
import com.dji.sikong.entity.ZhimouApp;
import com.dji.sikong.entity.ZhimouAppPackage;
import com.dji.sikong.entity.ZhimouTaskConfig;
import com.dji.sikong.mapper.ZhimouAppMapper;
import com.dji.sikong.mapper.ZhimouAppPackageMapper;
import com.dji.sikong.service.ZhimouAppSyncService;
import com.dji.sikong.service.ZhimouTaskConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/zhimou/config")
public class ZhimouTaskConfigController {

    @Resource
    private ZhimouTaskConfigService zhimouTaskConfigService;

    @Resource
    private ZhimouAppSyncService zhimouAppSyncService;

    @Resource
    private ZhimouAppPackageMapper zhimouAppPackageMapper;

    @Resource
    private ZhimouAppMapper zhimouAppMapper;

    @GetMapping("")
    public R<ZhimouTaskConfig> getConfig() {
        ZhimouTaskConfig config = zhimouTaskConfigService.getConfig();
        return R.ok(config);
    }

    @PostMapping("")
    public R<ZhimouTaskConfig> saveConfig(@RequestBody ZhimouTaskConfig config) {
        ZhimouTaskConfig saved = zhimouTaskConfigService.saveConfig(config);
        return R.ok(saved);
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteConfig(@PathVariable Long id) {
        zhimouTaskConfigService.deleteConfig(id);
        return R.ok();
    }

    @PostMapping("/sync")
    public R<Map<String, Object>> syncApps() {
        Map<String, Object> result = zhimouAppSyncService.syncFromZhimou();
        if (Boolean.TRUE.equals(result.get("success"))) {
            return R.ok(result);
        } else {
            return R.fail((String) result.get("message"));
        }
    }

    @GetMapping("/packages")
    public R<List<ZhimouAppPackage>> listPackages() {
        List<ZhimouAppPackage> packages = zhimouAppPackageMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ZhimouAppPackage>()
                .eq(ZhimouAppPackage::getDeleted, 0)
                .orderByDesc(ZhimouAppPackage::getSyncTime)
        );
        return R.ok(packages);
    }

    @GetMapping("/apps")
    public R<List<ZhimouApp>> listApps(@RequestParam(required = false) String zapk_id) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ZhimouApp> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ZhimouApp>()
                .eq(ZhimouApp::getDeleted, 0);
        
        if (zapk_id != null && !zapk_id.isEmpty()) {
            wrapper.eq(ZhimouApp::getZapkId, zapk_id);
        }
        
        wrapper.orderByDesc(ZhimouApp::getSyncTime);
        
        List<ZhimouApp> apps = zhimouAppMapper.selectList(wrapper);
        return R.ok(apps);
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("packageCount", zhimouAppSyncService.getPackageCount());
        stats.put("appCount", zhimouAppSyncService.getAppCount());
        return R.ok(stats);
    }
}
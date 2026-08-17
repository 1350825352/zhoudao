package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.entity.WorkOrderRule;
import com.dji.sikong.service.WorkOrderRuleService;
import com.dji.sikong.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/workorder/rule")
public class WorkOrderRuleController {

    @Resource
    private WorkOrderRuleService workOrderRuleService;

    @GetMapping("/list")
    public R<List<WorkOrderRule>> list() {
        try {
            List<WorkOrderRule> list = workOrderRuleService.list(new LambdaQueryWrapper<WorkOrderRule>()
                    .eq(WorkOrderRule::getDeleted, 0)
                    .orderByAsc(WorkOrderRule::getSortOrder));
            return R.ok(list);
        } catch (Exception e) {
            log.error("[工单规则] 查询失败", e);
            return R.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping
    public R<Void> create(@RequestBody WorkOrderRule rule) {
        try {
            workOrderRuleService.save(rule);
            log.info("[工单规则] 创建成功: id={}", rule.getId());
            return R.ok("创建成功", null);
        } catch (Exception e) {
            log.error("[工单规则] 创建失败", e);
            return R.fail("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody WorkOrderRule rule) {
        try {
            rule.setId(id);
            workOrderRuleService.updateById(rule);
            log.info("[工单规则] 更新成功: id={}", id);
            return R.ok("更新成功", null);
        } catch (Exception e) {
            log.error("[工单规则] 更新失败", e);
            return R.fail("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        try {
            workOrderRuleService.removeById(id);
            log.info("[工单规则] 删除成功: id={}", id);
            return R.ok("删除成功", null);
        } catch (Exception e) {
            log.error("[工单规则] 删除失败", e);
            return R.fail("删除失败: " + e.getMessage());
        }
    }
}
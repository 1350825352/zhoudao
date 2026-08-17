package com.dji.sikong.controller;

import com.dji.sikong.common.R;
import com.dji.sikong.entity.OrganizationProject;
import com.dji.sikong.service.OrganizationProjectService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/project")
@CrossOrigin(origins = "*")
public class OrganizationProjectController {

    @Resource
    private OrganizationProjectService projectService;

    @GetMapping("/list")
    public R<List<OrganizationProject>> list() {
        return R.ok(projectService.list());
    }

    @PostMapping("/sync")
    public R<List<OrganizationProject>> sync() {
        List<OrganizationProject> list = projectService.syncFromOpenApi();
        return R.ok("同步完成，共" + list.size() + "个项目", list);
    }

    @PostMapping
    public R<Boolean> save(@RequestBody OrganizationProject entity) {
        return R.ok(projectService.saveOrUpdate(entity));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(projectService.removeById(id));
    }
}

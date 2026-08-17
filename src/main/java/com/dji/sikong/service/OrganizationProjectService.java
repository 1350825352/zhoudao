package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.entity.OrganizationProject;

import java.util.List;

public interface OrganizationProjectService extends IService<OrganizationProject> {

    List<OrganizationProject> syncFromOpenApi();
}

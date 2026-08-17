package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.entity.Wayline;

import java.util.List;

public interface WaylineService extends IService<Wayline> {

    List<Wayline> syncFromOpenApi(String projectUuid);

    List<Wayline> listByProject(String projectUuid);

    Wayline getByUuid(String waylineUuid);
}

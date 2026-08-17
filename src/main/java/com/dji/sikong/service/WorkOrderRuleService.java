package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.entity.WorkOrderRule;

public interface WorkOrderRuleService extends IService<WorkOrderRule> {

    WorkOrderRule matchRule(String alarmType, String aircraftSn);
}
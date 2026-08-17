package com.dji.sikong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.entity.WorkOrderRule;
import com.dji.sikong.mapper.WorkOrderRuleMapper;
import com.dji.sikong.service.WorkOrderRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
public class WorkOrderRuleServiceImpl extends ServiceImpl<WorkOrderRuleMapper, WorkOrderRule> implements WorkOrderRuleService {

    @Override
    public WorkOrderRule matchRule(String alarmType, String aircraftSn) {
        LocalTime now = LocalTime.now();

        List<WorkOrderRule> rules = this.list(new LambdaQueryWrapper<WorkOrderRule>()
                .eq(WorkOrderRule::getStatus, 1)
                .eq(WorkOrderRule::getDeleted, 0)
                .orderByAsc(WorkOrderRule::getSortOrder));

        log.info("[工单规则] 参与匹配的启用规则数量={}, alarmType={}, aircraftSn={}, currentTime={}",
                rules.size(), alarmType, aircraftSn, now);

        for (WorkOrderRule rule : rules) {
            boolean alarmTypeMatch = rule.getAlarmType() == null || rule.getAlarmType().isEmpty()
                    || rule.getAlarmType().equals(alarmType);
            boolean aircraftMatch = rule.getAircraftSn() == null || rule.getAircraftSn().isEmpty()
                    || rule.getAircraftSn().equals(aircraftSn);

            LocalTime startTime = rule.getStartTime() != null ? rule.getStartTime() : LocalTime.of(0, 0, 0);
            LocalTime endTime = rule.getEndTime() != null ? rule.getEndTime() : LocalTime.of(23, 59, 59);

            boolean timeMatch = !now.isBefore(startTime) && !now.isAfter(endTime);

            log.info("[工单规则] 检查规则id={}: alarmTypeMatch={} (rule={}), aircraftMatch={} (rule={}), timeMatch={} ({} - {})",
                    rule.getId(), alarmTypeMatch, rule.getAlarmType(), aircraftMatch, rule.getAircraftSn(), timeMatch, startTime, endTime);

            if (alarmTypeMatch && aircraftMatch && timeMatch) {
                log.info("[工单规则] 匹配到规则: id={}, handlerName={}, handlerEmail={}",
                        rule.getId(), rule.getHandlerName(), rule.getHandlerEmail());
                return rule;
            }
        }

        log.info("[工单规则] 未匹配到规则: alarmType={}, aircraftSn={}", alarmType, aircraftSn);
        return null;
    }
}
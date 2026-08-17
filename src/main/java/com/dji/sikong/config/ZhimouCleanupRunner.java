package com.dji.sikong.config;

import com.dji.sikong.service.ZhimouTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * SpringBoot启动完成后: 清理知眸侧在跑、但本地已经停止的僵尸任务
 * 每天凌晨3点: 清理超过24小时的历史直播流和知眸任务记录
 */
@Slf4j
@Component
public class ZhimouCleanupRunner implements ApplicationRunner {

    @Resource
    private ZhimouTaskService zhimouTaskService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("[知眸-启动清理] 开始清理僵尸AI任务...");
            zhimouTaskService.cleanupZombieTasksOnStartup();
            log.info("[知眸-启动清理] 完成");
        } catch (Exception e) {
            log.warn("[知眸-启动清理] 异常(不影响主服务启动)", e);
        }
        try {
            log.info("[知眸-启动清理] 开始清理超过24小时的历史记录...");
            int deleted = zhimouTaskService.cleanupOldHistoryRecords(24);
            log.info("[知眸-启动清理] 历史记录清理完成, 共删除{}条", deleted);
        } catch (Exception e) {
            log.warn("[知眸-启动清理] 历史记录清理异常(不影响主服务启动)", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyCleanup() {
        try {
            log.info("[知眸-定时清理] 开始清理超过24小时的历史记录...");
            int deleted = zhimouTaskService.cleanupOldHistoryRecords(24);
            log.info("[知眸-定时清理] 完成, 共删除{}条", deleted);
        } catch (Exception e) {
            log.warn("[知眸-定时清理] 异常", e);
        }
    }
}

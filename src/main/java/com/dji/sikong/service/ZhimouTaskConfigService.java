package com.dji.sikong.service;

import com.dji.sikong.entity.ZhimouTaskConfig;

public interface ZhimouTaskConfigService {

    ZhimouTaskConfig getConfig();

    ZhimouTaskConfig saveConfig(ZhimouTaskConfig config);

    void deleteConfig(Long id);
}
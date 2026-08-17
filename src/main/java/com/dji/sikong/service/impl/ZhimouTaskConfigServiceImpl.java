package com.dji.sikong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.entity.ZhimouTaskConfig;
import com.dji.sikong.mapper.ZhimouTaskConfigMapper;
import com.dji.sikong.service.ZhimouTaskConfigService;
import org.springframework.stereotype.Service;

@Service
public class ZhimouTaskConfigServiceImpl implements ZhimouTaskConfigService {

    private final ZhimouTaskConfigMapper zhimouTaskConfigMapper;

    public ZhimouTaskConfigServiceImpl(ZhimouTaskConfigMapper zhimouTaskConfigMapper) {
        this.zhimouTaskConfigMapper = zhimouTaskConfigMapper;
    }

    @Override
    public ZhimouTaskConfig getConfig() {
        LambdaQueryWrapper<ZhimouTaskConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(ZhimouTaskConfig::getDeleted, 0);
        return zhimouTaskConfigMapper.selectOne(qw);
    }

    @Override
    public ZhimouTaskConfig saveConfig(ZhimouTaskConfig config) {
        if (config.getId() == null) {
            config.setDeleted(0);
            zhimouTaskConfigMapper.insert(config);
        } else {
            zhimouTaskConfigMapper.updateById(config);
        }
        return config;
    }

    @Override
    public void deleteConfig(Long id) {
        ZhimouTaskConfig config = new ZhimouTaskConfig();
        config.setId(id);
        config.setDeleted(1);
        zhimouTaskConfigMapper.updateById(config);
    }
}
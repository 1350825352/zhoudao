package com.dji.sikong.service;

import java.util.Map;

public interface ZhimouAppSyncService {

    Map<String, Object> syncFromZhimou();

    int getPackageCount();

    int getAppCount();
}
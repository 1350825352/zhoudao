package com.dji.sikong.common;

public interface RedisKeyConstants {

    String PREFIX = "sikong:";

    String DEVICE_ONLINE_KEY = PREFIX + "device:online:";
    String DEVICE_REALTIME_KEY = PREFIX + "device:realtime:";
    String DEVICE_INFO_KEY = PREFIX + "device:info:";
    String GATEWAY_SUB_DEVICES_KEY = PREFIX + "gateway:sub_devices:";

    String TASK_TID_BID_KEY = PREFIX + "task:tid_bid:";
    String LIVE_TID_BID_KEY = PREFIX + "live:tid_bid:";

    String WAYLINE_CACHE_KEY = PREFIX + "wayline:";
    String PROJECT_CACHE_KEY = PREFIX + "project:";

    long DEFAULT_EXPIRE_SECONDS = 24 * 60 * 60L;
    long REALTIME_EXPIRE_SECONDS = 10 * 60L;
    long TID_MATCH_EXPIRE_SECONDS = 5 * 60L;
}

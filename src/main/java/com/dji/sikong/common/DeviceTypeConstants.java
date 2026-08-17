package com.dji.sikong.common;

public interface DeviceTypeConstants {

    int GATEWAY_DOCK = 1;
    int GATEWAY_REMOTE = 2;
    int AIRCRAFT = 3;
    int CAMERA = 4;
    int LOUDSPEAKER = 5;
    int SPOTLIGHT = 6;

    int ONLINE_OFFLINE = 0;
    int ONLINE_ONLINE = 1;

    int PAIRED_NO = 0;
    int PAIRED_YES = 1;

    int DOCK_IN_NO = 0;
    int DOCK_IN_YES = 1;

    int CHARGE_STATE_NONE = 0;
    int CHARGE_STATE_CHARGING = 1;
    int CHARGE_STATE_FULL = 2;

    String CAMERA_INDEX_WIDE = "wide";
    String CAMERA_INDEX_ZOOM = "zoom";
    String CAMERA_INDEX_IR = "ir";
}

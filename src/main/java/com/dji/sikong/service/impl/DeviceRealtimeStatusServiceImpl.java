package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.common.RedisKeyConstants;
import com.dji.sikong.dto.StartLiveStreamDTO;
import com.dji.sikong.entity.AircraftCamera;
import com.dji.sikong.entity.DeviceRealtimeStatus;
import com.dji.sikong.mapper.AircraftCameraMapper;
import com.dji.sikong.mapper.DeviceRealtimeStatusMapper;
import com.dji.sikong.service.DeviceRealtimeStatusService;
import com.dji.sikong.service.LiveStreamService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceRealtimeStatusServiceImpl
        extends ServiceImpl<DeviceRealtimeStatusMapper, DeviceRealtimeStatus>
        implements DeviceRealtimeStatusService {

    @Resource
    private DeviceRealtimeStatusMapper statusMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Lazy
    @Resource
    private LiveStreamService liveStreamService;

    @Lazy
    @Resource
    private AircraftCameraMapper aircraftCameraMapper;

    @Resource
    private com.dji.sikong.mapper.DeviceMapper deviceMapper;

    @Lazy
    @Resource
    private com.dji.sikong.service.ZhimouTaskService zhimouTaskService;

    /** 降落锁定(防止OSD抖动导致反复开关): 降落后2分钟内不允许自动开启码流 */
    private static final int LANDED_LOCK_SECONDS = 2 * 60;
    /** 降落防抖观察期(秒): 连续5秒收到非飞行mode才真正关码流 */
    private static final int LANDING_OBSERVE_SECONDS = 5;
    private static String landedLockKey(String aircraftSn) {
        return RedisKeyConstants.DEVICE_REALTIME_KEY + ":live-landed-lock:" + aircraftSn;
    }
    /** 降落观察状态 key=飞机SN, value=[首次观察到非飞行mode的毫秒时间戳, 当前mode_code](Thread安全) */
    private static final ConcurrentHashMap<String, long[]> LANDING_OBSERVE_MAP = new ConcurrentHashMap<>();

    // ========= 飞行/地面mode_code集合(与DjiMqttClient.handleDrcUp保持一致,唯一判断标准) =========
    // 飞行集合(开码流,且清降落锁): 3手动/悬停 /4自动起飞 /5航线飞行 /6全景拍照 /7智能跟随 /8ADS-B躲避
    //                            /9自动返航 /10自动降落 /11强制降落 /12三桨叶降落
    //                            /15APAS /16虚拟摇杆 /17指令飞行 /19 /20 /21
    private static final java.util.Set<Integer> FLYING_MODES = new java.util.HashSet<>(
            java.util.Arrays.asList(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16, 17, 19, 20, 21));
    private static final java.util.Set<Integer> GROUND_LANDING_MODES = new java.util.HashSet<>(
            java.util.Arrays.asList(0, 1, 2));

    @Override
    public void saveOrUpdateFromOsd(String deviceSn, String gatewaySn, JsonNode osdNode) {
        if (StrUtil.isBlank(deviceSn) || osdNode == null) return;
        DeviceRealtimeStatus exist = statusMapper.selectOne(
                new LambdaQueryWrapper<DeviceRealtimeStatus>().eq(DeviceRealtimeStatus::getDeviceSn, deviceSn));
        DeviceRealtimeStatus status = exist == null ? new DeviceRealtimeStatus() : exist;
        status.setDeviceSn(deviceSn);
        if (StrUtil.isNotBlank(gatewaySn)) status.setGatewaySn(gatewaySn);

        JsonNode data = osdNode.has("host") ? osdNode.get("host") : osdNode;

        // ========================================================
        // 新增: 解析真正的飞机SN(从sub_device.device_sn)和mode_code(飞行器状态)
        // ========================================================
        String realAircraftSn = null;
        // 1. 优先从 root 直接的sub_device或data.sub_device取
        JsonNode subDev = osdNode.has("sub_device") ? osdNode.get("sub_device")
                : (data.has("sub_device") ? data.get("sub_device") : null);
        if (subDev != null && subDev.has("device_sn") && StrUtil.isNotBlank(subDev.get("device_sn").asText())) {
            realAircraftSn = subDev.get("device_sn").asText();
        }
        // 从root或data中查找best_link_gateway
        String bestLinkGateway = null;
        if (osdNode.has("best_link_gateway") && StrUtil.isNotBlank(osdNode.get("best_link_gateway").asText())) {
            bestLinkGateway = osdNode.get("best_link_gateway").asText();
        } else if (data.has("best_link_gateway") && StrUtil.isNotBlank(data.get("best_link_gateway").asText())) {
            bestLinkGateway = data.get("best_link_gateway").asText();
        }
        // 2. 其次: 如果有 best_link_gateway,且deviceSn!=它 → deviceSn本身就是飞机
        if (StrUtil.isBlank(realAircraftSn) && StrUtil.isNotBlank(bestLinkGateway)) {
            if (!bestLinkGateway.equals(deviceSn)) {
                realAircraftSn = deviceSn;
            }
        }
        // 3. 回退: 用deviceSn本身
        if (StrUtil.isBlank(realAircraftSn)) realAircraftSn = deviceSn;
        // 确定最终gatewaySn:优先realAircraftSn对应的gatewaySn
        String finalGw = (StrUtil.isNotBlank(gatewaySn) ? gatewaySn
                : (realAircraftSn.equals(deviceSn) && StrUtil.isNotBlank(bestLinkGateway)
                ? bestLinkGateway : realAircraftSn));
        // 解析mode_code(飞行器状态,在OSD中data.host里)
        Integer modeCode = null;
        if (data.has("mode_code") && !data.get("mode_code").isNull()) {
            modeCode = data.get("mode_code").asInt();
        }
        System.out.println("[OSD-状态解析] deviceSn=" + deviceSn + " realAircraftSn=" + realAircraftSn
                + " finalGw=" + finalGw + " mode_code=" + modeCode);

        // 位置
        if (data.has("latitude")) status.setLatitude(getDecimal(data, "latitude"));
        if (data.has("longitude")) status.setLongitude(getDecimal(data, "longitude"));
        if (data.has("height")) status.setHeight(getDecimal(data, "height"));
        // altitude = height (绝对高度, OSD的height就是海拔高度)
        if (data.has("altitude")) {
            status.setAltitude(getDecimal(data, "altitude"));
        } else {
            status.setAltitude(status.getHeight());
        }
        // elevation = 相对起飞点高度
        if (data.has("elevation")) status.setElevation(getDecimal(data, "elevation"));
        if (data.has("vertical_speed")) status.setVerticalSpeed(getDecimal(data, "vertical_speed"));
        if (data.has("horizontal_speed")) status.setHorizontalSpeed(getDecimal(data, "horizontal_speed"));
        // home位置 - 优先从data直接取，其次从data的home子节点或dock子节点取
        boolean hasHomeDirect = data.has("home_latitude") && data.has("home_longitude");
        if (hasHomeDirect) {
            status.setHomeLatitude(getDecimal(data, "home_latitude"));
            status.setHomeLongitude(getDecimal(data, "home_longitude"));
        } else {
            JsonNode homeNode = data.has("home") ? data.get("home") : (data.has("dock") ? data.get("dock") : null);
            if (homeNode != null) {
                if (homeNode.has("latitude")) status.setHomeLatitude(getDecimal(homeNode, "latitude"));
                if (homeNode.has("longitude")) status.setHomeLongitude(getDecimal(homeNode, "longitude"));
            }
        }
        // 距离Home - 优先取home_distance(DJI OSD标准字段), 其次取distance_to_home
        BigDecimal hd = data.has("home_distance") ? getDecimal(data, "home_distance") : null;
        BigDecimal dth = data.has("distance_to_home") ? getDecimal(data, "distance_to_home") : null;
        if (hd != null && hd.doubleValue() > 0) {
            status.setDistanceToHome(hd);
        } else if (dth != null && dth.doubleValue() > 0) {
            status.setDistanceToHome(dth);
        } else if (dth != null) {
            status.setDistanceToHome(dth);
        } else if (hd != null) {
            status.setDistanceToHome(hd);
        }

        // 距离Home - 如果没有，实时计算
        if (status.getLatitude() != null && status.getLongitude() != null
                && status.getHomeLatitude() != null && status.getHomeLongitude() != null
                && status.getDistanceToHome() == null) {
            status.setDistanceToHome(calculateDistance(
                    status.getLatitude(), status.getLongitude(),
                    status.getHomeLatitude(), status.getHomeLongitude()));
        }

        // 飞行状态
if (data.has("device_mode")) status.setDeviceMode(data.get("device_mode").asInt());
// mode_code(飞行器状态)也存到deviceMode字段
if (modeCode != null) status.setDeviceMode(modeCode);
        if (data.has("flight_status")) status.setFlightStatus(data.get("flight_status").asText());
        if (data.has("drone_in_dock")) status.setDroneInDock(data.get("drone_in_dock").asInt());
        if (data.has("cover_state")) status.setCoverState(data.get("cover_state").asInt());
        if (data.has("flight_step_code")) status.setFlightStepCode(data.get("flight_step_code").asInt());
if (data.has("flighttask_step_code")) status.setFlightStepCode(data.get("flighttask_step_code").asInt());
        if (data.has("current_task_id")) status.setCurrentTaskId(data.get("current_task_id").asText());
        if (data.has("current_task_name")) status.setCurrentTaskName(data.get("current_task_name").asText());
        if (data.has("wayline_uuid")) status.setWaylineUuid(data.get("wayline_uuid").asText());
        if (data.has("wayline_id")) status.setWaylineUuid(data.get("wayline_id").asText());

        // 电池 - 可能在data.battery或data.battery.batteries数组中
        if (data.has("battery")) {
            JsonNode bat = data.get("battery");
            if (bat.has("capacity_percent")) status.setBatteryPercent(bat.get("capacity_percent").asInt());
            if (bat.has("temperature")) status.setBatteryTemperature(getDecimal(bat, "temperature"));
            if (bat.has("voltage")) status.setBatteryVoltage(bat.get("voltage").asInt());
            if (bat.has("current")) status.setBatteryCurrent(bat.get("current").asInt());
            if (bat.has("batteries")) {
                JsonNode batts = bat.get("batteries");
                if (batts.isArray() && batts.size() > 0) {
                    JsonNode firstBat = batts.get(0);
                    if (firstBat.has("capacity_percent") && status.getBatteryPercent() == null)
                        status.setBatteryPercent(firstBat.get("capacity_percent").asInt());
                    if (firstBat.has("temperature") && status.getBatteryTemperature() == null)
                        status.setBatteryTemperature(getDecimal(firstBat, "temperature"));
                }
            }
        }
        // 机场的 drone_battery_maintenance_info.batteries（在host直接下）
        if (data.has("drone_battery_maintenance_info")) {
            JsonNode info = data.get("drone_battery_maintenance_info");
            if (info.has("batteries")) {
                JsonNode batts = info.get("batteries");
                if (batts.isArray() && batts.size() > 0) {
                    JsonNode firstBat = batts.get(0);
                    if (firstBat.has("capacity_percent") && status.getBatteryPercent() == null)
                        status.setBatteryPercent(firstBat.get("capacity_percent").asInt());
                    if (firstBat.has("temperature") && status.getBatteryTemperature() == null)
                        status.setBatteryTemperature(getDecimal(firstBat, "temperature"));
                }
            }
        }
        // 机场备用电池 backup_battery.temperature
        if (data.has("backup_battery")) {
            JsonNode bb = data.get("backup_battery");
            if (bb.has("temperature") && status.getBatteryTemperature() == null)
                status.setBatteryTemperature(getDecimal(bb, "temperature"));
        }
        if (data.has("batteries")) {
            JsonNode batts = data.get("batteries");
            if (batts.isArray() && batts.size() > 0) {
                JsonNode firstBat = batts.get(0);
                if (firstBat.has("capacity_percent") && status.getBatteryPercent() == null)
                    status.setBatteryPercent(firstBat.get("capacity_percent").asInt());
                if (firstBat.has("temperature") && status.getBatteryTemperature() == null)
                    status.setBatteryTemperature(getDecimal(firstBat, "temperature"));
            }
        }
        // 兼容直接字段
        if (data.has("battery_percent") && status.getBatteryPercent() == null)
            status.setBatteryPercent(data.get("battery_percent").asInt());
        if (data.has("battery_temperature") && status.getBatteryTemperature() == null)
            status.setBatteryTemperature(getDecimal(data, "battery_temperature"));
        if (data.has("drone_charge_state")) {
            JsonNode cs = data.get("drone_charge_state");
            if (cs.has("state")) status.setChargeState(cs.get("state").asInt());
            if (cs.has("capacity_percent") && status.getBatteryPercent() == null)
                status.setBatteryPercent(cs.get("capacity_percent").asInt());
        }

        // GPS/RTK
        if (data.has("position_state")) {
            JsonNode ps = data.get("position_state");
            if (ps.has("gps_number")) status.setGpsSatelliteCount(ps.get("gps_number").asInt());
            if (ps.has("rtk_number")) status.setRtkSatelliteCount(ps.get("rtk_number").asInt());
            if (ps.has("is_fixed")) status.setRtkFixed(ps.get("is_fixed").asInt());
            if (ps.has("quality")) status.setPositionQuality(ps.get("quality").asInt());
            if (ps.has("calibration_state")) status.setCalibrationState(ps.get("calibration_state").asInt());
        }
        if (data.has("gps_satellite_count") && status.getGpsSatelliteCount() == null)
            status.setGpsSatelliteCount(data.get("gps_satellite_count").asInt());

        // 环境
        if (data.has("wind_speed")) status.setWindSpeed(getDecimal(data, "wind_speed"));
        if (data.has("environment_temperature")) status.setEnvironmentTemperature(getDecimal(data, "environment_temperature"));
        if (data.has("humidity")) status.setHumidity(data.get("humidity").asInt());
        if (data.has("rainfall")) status.setRainfall(data.get("rainfall").asInt());

        // 网络
        if (data.has("network_state")) {
            JsonNode net = data.get("network_state");
            if (net.has("type")) status.setNetworkType(net.get("type").asInt());
            if (net.has("quality")) status.setNetworkQuality(net.get("quality").asInt());
        }
        if (data.has("sdr_link_state")) status.setSdrLinkState(data.get("sdr_link_state").asInt());
        if (data.has("4g_link_state")) status.setLink4gState(data.get("4g_link_state").asInt());

        // 存储
        if (data.has("storage")) {
            JsonNode st = data.get("storage");
            if (st.has("total")) status.setStorageTotal(st.get("total").asLong());
            if (st.has("used")) status.setStorageUsed(st.get("used").asLong());
        }

        try {
            status.setOsdRawJson(osdNode.toString());
        } catch (Exception ignore) {}

        // 显式设置update_time, 因为strictUpdateFill不覆盖已有值
        status.setUpdateTime(java.time.LocalDateTime.now());

        if (exist == null) {
            statusMapper.insert(status);
        } else {
            statusMapper.updateById(status);
        }

        // 如果当前OSD是机场消息但解析出了飞机SN和mode_code, 同步更新飞机的status
        if (StrUtil.isNotBlank(realAircraftSn) && !realAircraftSn.equals(deviceSn) && modeCode != null) {
            try {
                DeviceRealtimeStatus aircraftStatus = statusMapper.selectOne(
                        new LambdaQueryWrapper<DeviceRealtimeStatus>().eq(DeviceRealtimeStatus::getDeviceSn, realAircraftSn));
                if (aircraftStatus != null) {
                    // 只有飞机的update_time超过30秒才更新(避免高频覆盖飞机自己的OSD数据)
                    boolean stale = aircraftStatus.getUpdateTime() == null
                            || aircraftStatus.getUpdateTime().isBefore(java.time.LocalDateTime.now().minusSeconds(30));
                    if (stale) {
                        aircraftStatus.setDeviceMode(modeCode);
                        aircraftStatus.setUpdateTime(java.time.LocalDateTime.now());
                        statusMapper.updateById(aircraftStatus);
                        System.out.println("[OSD-飞机状态同步] 从机场OSD同步飞机 mode_code=" + modeCode
                                + " 到飞机=" + realAircraftSn + " (飞机自己OSD超时未更新)");
                    }
                }
            } catch (Exception e) {
                System.out.println("[OSD-飞机状态同步] 失败: " + e.getMessage());
            }
        }

        // 缓存
        try {
            String key = RedisKeyConstants.DEVICE_REALTIME_KEY + deviceSn;
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(status),
                    RedisKeyConstants.REALTIME_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ignore) {}

        // ==================== 【核心】mode_code → 自动开关旁路直播（唯一判断标准,与DRC通道共用逻辑） ====================
        try {
            if (modeCode != null) {
                handleModeCode(realAircraftSn, finalGw, modeCode);
            }
        } catch (Exception e) {
            // 直播逻辑异常不影响主流程OSD入库
            System.out.println("[自动直播] 逻辑异常 aircraftSn=" + realAircraftSn + ", err=" + e.getMessage());
        }
    }

    /**
     * 统一mode_code开/关码流逻辑(OSD通道和DRC通道共用):
     *   飞行集合 FLYING_MODES → 立即生效(清降落观察状态+清锁+开码流)
     *   非飞行集合 → 进入5秒连续观察期,确认降落后 → 只关码流转发器(不更新status,不关知眸)
     *     视频播放/知眸任务的停止由2秒调度器根据SRS流状态单独判断
     */
    @Override
    public void handleModeCode(String aircraftSn, String gatewaySn, int modeCode) {
        if (StrUtil.isBlank(aircraftSn) || StrUtil.isBlank(gatewaySn)) return;
        String lockKey = landedLockKey(aircraftSn);
        if (FLYING_MODES.contains(modeCode)) {
            // ====== 飞行状态:立即生效(切回飞行马上作废之前的降落观察记录) ======
            LANDING_OBSERVE_MAP.remove(aircraftSn);
            // 先清降落锁定(允许正常播),因为悬停也是飞行
            try { stringRedisTemplate.delete(lockKey); } catch(Exception ignore) {}
            // ====== 开码流前再确认一次锁是否已清 ======
            Boolean landedLocked = null;
            try { landedLocked = stringRedisTemplate.hasKey(lockKey); } catch(Exception ignore) {}
            if (Boolean.TRUE.equals(landedLocked)) {
                System.out.println("[OSD-mode-开-拦截] 降落锁定中(mode=" + modeCode + "),飞机=" + aircraftSn + " → 不允许开播");
                return;
            }
            // 先清防重锁
            try { stringRedisTemplate.delete(RedisKeyConstants.DEVICE_REALTIME_KEY + ":live-auto-start:" + aircraftSn); } catch(Exception ignore) {}
            if (liveStreamService.syncAndHasActiveLive(aircraftSn)) {
                // 司空2说有活跃推流,但本地DB可能还没同步到status=1(比如旧的status=0脏数据)
                // → 只有本地status=1才算真正有活跃直播,否则还是要走startByPassLive同步状态并触发知眸任务
                if (liveStreamService.hasActiveLive(aircraftSn)) {
                    System.out.println("[OSD-mode-开] mode_code=" + modeCode + " 飞机=" + aircraftSn + " 已有活跃直播(status=1)→跳过");
                    return;
                }
                System.out.println("[OSD-mode-开-状态同步ℹ️] mode_code=" + modeCode + " 飞机=" + aircraftSn + " 司空2有推流但本地status≠1 → 继续走startByPassLive同步状态");
            }
            String cameraIndex = getFirstCameraIndex(aircraftSn);
            StartLiveStreamDTO dto = new StartLiveStreamDTO();
            dto.setGatewaySn(gatewaySn);
            dto.setAircraftSn(aircraftSn);
            dto.setCameraIndex(cameraIndex);
            dto.setStreamProtocol("rtmp");
            dto.setQualityType("adaptive");
            try {
                liveStreamService.startByPassLive(dto);
                System.out.println("[OSD-mode-开✅] mode_code=" + modeCode + "(飞行中) 飞机=" + aircraftSn + " → 自动开码流, camera=" + cameraIndex);
            } catch (Exception e) {
                System.out.println("[OSD-mode-开❌] mode_code=" + modeCode + " 飞机=" + aircraftSn + " 开播失败:" + e.getMessage());
            }
        } else {
            // ====== 非飞行状态:进入5秒连续观察期 ======
            long now = System.currentTimeMillis();
            long[] observe = LANDING_OBSERVE_MAP.get(aircraftSn);
            if (observe == null) {
                observe = new long[]{now, modeCode};
                LANDING_OBSERVE_MAP.put(aircraftSn, observe);
                System.out.println("[OSD-mode-关-观察👀] mode_code=" + modeCode + " 飞机=" + aircraftSn + " → 进入降落观察期(" + LANDING_OBSERVE_SECONDS + "s)");
            } else {
                long elapsed = now - observe[0];
                observe[1] = modeCode;
                if (elapsed >= LANDING_OBSERVE_SECONDS * 1000L) {
                    // 连续 LANDING_OBSERVE_SECONDS 秒非飞行mode → 确认降落
                    LANDING_OBSERVE_MAP.remove(aircraftSn);
                    // 加降落锁定(3分钟内不允许自动开播,防止一落地就又被OSD数据拉起)
                    try {
                        stringRedisTemplate.opsForValue().set(lockKey, "1", LANDED_LOCK_SECONDS, TimeUnit.SECONDS);
                    } catch(Exception ignore) {}
                    // 关码流转发器(司空2侧) + 更新live_stream.status=2
                    // → status是码流转发器的参数,飞机降落时应该更新为2
                    // → 知眸任务由2秒调度器根据SRS流情况独立管理,这里不碰知眸
                    try {
                        liveStreamService.stopStreamConverterOnly(aircraftSn);
                        liveStreamService.markLiveStreamStopped(aircraftSn, "飞机降落,关闭码流转发器");
                    } catch (Exception e) {
                        System.out.println("[OSD-mode-关❌] 飞机=" + aircraftSn + " 停码流转发器失败:" + e.getMessage());
                    }
                    System.out.println("[OSD-mode-关✅] mode_code=" + modeCode + "(非飞行) 飞机=" + aircraftSn + " → 确认降落,已停码流转发器+status=2(知眸由调度器独立管理)");
                } else {
                    if (elapsed % 2000 < 100) {
                        System.out.println("[OSD-mode-关-防抖⏳] 飞机=" + aircraftSn + " 非飞行mode连续=" + (elapsed/1000.0) + "s < " + LANDING_OBSERVE_SECONDS + "s → 继续观察");
                    }
                }
            }
        }
    }

    /** 查询飞机的第一个cameraIndex, 查不到默认返回 "0" */
    private String getFirstCameraIndex(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return "0";
        try {
            AircraftCamera cam = aircraftCameraMapper.selectOne(new LambdaQueryWrapper<AircraftCamera>()
                    .eq(AircraftCamera::getAircraftSn, aircraftSn)
                    .orderByAsc(AircraftCamera::getId)
                    .last(" LIMIT 1"));
            if (cam != null && StrUtil.isNotBlank(cam.getCameraIndex())) {
                return cam.getCameraIndex();
            }
        } catch (Exception ignore) {
        }
        return "0";
    }

    @Override
    public DeviceRealtimeStatus getByDeviceSn(String deviceSn) {
        if (StrUtil.isBlank(deviceSn)) return null;
        // 先查缓存
        String key = RedisKeyConstants.DEVICE_REALTIME_KEY + deviceSn;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cached)) {
            try { return JSONUtil.toBean(cached, DeviceRealtimeStatus.class); } catch (Exception ignore) {}
        }
        DeviceRealtimeStatus status = statusMapper.selectOne(
                new LambdaQueryWrapper<DeviceRealtimeStatus>().eq(DeviceRealtimeStatus::getDeviceSn, deviceSn));
        if (status != null) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(status),
                    RedisKeyConstants.REALTIME_EXPIRE_SECONDS, TimeUnit.SECONDS);
        }
        return status;
    }

    private BigDecimal getDecimal(JsonNode node, String field) {
        if (!node.has(field)) return null;
        try {
            return new BigDecimal(node.get(field).asText());
        } catch (Exception e) {
            return null;
        }
    }

    /** Haversine距离计算(米) */
    private BigDecimal calculateDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return new BigDecimal(earthRadius * c).setScale(2, BigDecimal.ROUND_HALF_UP);
    }
}

package com.dji.sikong.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.entity.Device;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.entity.WorkOrder;
import com.dji.sikong.entity.WorkOrderImage;
import com.dji.sikong.entity.WorkOrderRule;
import com.dji.sikong.entity.ZhimouDetectionResult;
import com.dji.sikong.mapper.DeviceMapper;
import com.dji.sikong.mapper.WaylineMapper;
import com.dji.sikong.mapper.WorkOrderImageMapper;
import com.dji.sikong.mapper.WorkOrderMapper;
import com.dji.sikong.mapper.ZhimouDetectionResultMapper;
import com.dji.sikong.service.MailService;
import com.dji.sikong.service.WorkOrderRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/workorder")
@CrossOrigin(origins = "*")
public class WorkOrderController {

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Resource
    private WorkOrderImageMapper workOrderImageMapper;

    @Resource
    private ZhimouDetectionResultMapper detectionResultMapper;

    @Resource
    private MailService mailService;

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private WaylineMapper waylineMapper;

    @Resource
    private WorkOrderRuleService workOrderRuleService;

    @Value("${zhimou.image.save-root-dir}")
    private String imageSaveRootDir;

    @Value("${zhimou.image.url-prefix}")
    private String imageUrlPrefix;

    @PostMapping("/create")
    public R<Map<String, Object>> createWorkOrder(@RequestBody Map<String, Object> body) {
        try {
            Long alarmId = body.get("alarm_id") != null ? Long.valueOf(String.valueOf(body.get("alarm_id"))) : null;
            String handlerEmail = body.get("handler_email") != null ? String.valueOf(body.get("handler_email")) : null;
            String handlerName = body.get("handler_name") != null ? String.valueOf(body.get("handler_name")) : null;

            if (alarmId == null) {
                return R.fail("alarm_id 必填");
            }

            ZhimouDetectionResult result = detectionResultMapper.selectById(alarmId);
            if (result == null) {
                return R.fail("告警记录不存在");
            }

            if (result.getWorkOrderId() != null) {
                return R.fail("该告警已转工单");
            }

            String workOrderCode = generateWorkOrderCode();
            String h5Token = IdUtil.simpleUUID();

            Device aircraft = null;
            if (StrUtil.isNotBlank(result.getAircraftSn())) {
                aircraft = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                        .eq(Device::getSn, result.getAircraftSn())
                        .eq(Device::getDeleted, 0));
            }

            log.info("[工单-规则匹配] 开始匹配: ruleName={}, aircraftSn={}, inputHandlerEmail={}",
                    result.getRuleName(), result.getAircraftSn(), handlerEmail);
            if (StrUtil.isBlank(handlerEmail)) {
                WorkOrderRule rule = workOrderRuleService.matchRule(result.getRuleName(), result.getAircraftSn());
                if (rule != null) {
                    handlerEmail = rule.getHandlerEmail();
                    handlerName = rule.getHandlerName();
                    log.info("[工单-规则匹配] 匹配成功: ruleId={}, handlerName={}, handlerEmail={}",
                            rule.getId(), handlerName, handlerEmail);
                } else {
                    log.info("[工单-规则匹配] 未匹配到任何规则");
                }
            } else {
                log.info("[工单-规则匹配] 跳过规则匹配: 已指定 handlerEmail={}", handlerEmail);
            }

            String waylineName = null;
            if (StrUtil.isNotBlank(result.getWaylineUuid())) {
                Wayline wayline = waylineMapper.selectOne(new LambdaQueryWrapper<Wayline>()
                        .eq(Wayline::getWaylineUuid, result.getWaylineUuid())
                        .eq(Wayline::getDeleted, 0));
                if (wayline != null) {
                    waylineName = wayline.getWaylineName();
                }
            }

            WorkOrder workOrder = new WorkOrder();
            workOrder.setCode(workOrderCode);
            workOrder.setAlarmId(alarmId);
            workOrder.setAlarmType(result.getRuleName());
            workOrder.setAircraftSn(result.getAircraftSn());
            workOrder.setAircraftName(aircraft != null ? aircraft.getDeviceName() : null);
            workOrder.setWaylineUuid(result.getWaylineUuid());
            workOrder.setWaylineName(waylineName != null ? waylineName : "手动飞行");
            workOrder.setAlarmImagePath(result.getImageRelativePath());
            workOrder.setHandlerEmail(handlerEmail);
            workOrder.setHandlerName(handlerName);
            workOrder.setStatus("PENDING");
            workOrder.setH5Token(h5Token);
            workOrder.setLatitude(result.getLatitude());
            workOrder.setLongitude(result.getLongitude());
            workOrder.setEventTime(result.getEventTime());
            workOrderMapper.insert(workOrder);

            LambdaUpdateWrapper<ZhimouDetectionResult> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ZhimouDetectionResult::getId, alarmId)
                    .set(ZhimouDetectionResult::getWorkOrderId, workOrder.getId())
                    .set(ZhimouDetectionResult::getUpdateTime, LocalDateTime.now());
            detectionResultMapper.update(null, updateWrapper);

            log.info("[工单] 邮件配置状态: isConfigured={}, handlerEmail={}", mailService.isConfigured(), handlerEmail);
            if (mailService.isConfigured() && StrUtil.isNotBlank(handlerEmail)) {
                mailService.sendWorkOrderEmail(handlerEmail, handlerName, workOrderCode, result.getRuleName(), h5Token);
                log.info("[工单] 邮件已发送");
            } else {
                log.info("[工单] 邮件未发送: isConfigured={}, handlerEmail={}", mailService.isConfigured(), handlerEmail);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("work_order_id", workOrder.getId());
            data.put("work_order_code", workOrderCode);
            data.put("alarm_id", alarmId);
            data.put("h5_token", h5Token);
            log.info("[工单] 创建工单成功, work_order_id={}, code={}, alarm_id={}", workOrder.getId(), workOrderCode, alarmId);
            return R.ok("转工单成功", data);
        } catch (Exception e) {
            log.error("[工单] 创建工单失败", e);
            return R.fail("创建工单失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public R<Map<String, Object>> listWorkOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String aircraftSn,
            @RequestParam(required = false) String code) {
        try {
            Page<WorkOrder> pageParam = new Page<>(page, size);
            LambdaQueryWrapper<WorkOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(WorkOrder::getDeleted, 0);

            if (StrUtil.isNotBlank(status)) {
                queryWrapper.eq(WorkOrder::getStatus, status);
            }
            if (StrUtil.isNotBlank(alarmType)) {
                queryWrapper.like(WorkOrder::getAlarmType, alarmType);
            }
            if (StrUtil.isNotBlank(aircraftSn)) {
                queryWrapper.like(WorkOrder::getAircraftSn, aircraftSn);
            }
            if (StrUtil.isNotBlank(code)) {
                queryWrapper.like(WorkOrder::getCode, code);
            }

            queryWrapper.orderByDesc(WorkOrder::getCreateTime);
            IPage<WorkOrder> result = workOrderMapper.selectPage(pageParam, queryWrapper);

            List<Map<String, Object>> list = new ArrayList<>();
            for (WorkOrder order : result.getRecords()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", order.getId());
                item.put("code", order.getCode());
                item.put("alarm_id", order.getAlarmId());
                item.put("alarm_type", order.getAlarmType());
                item.put("aircraft_sn", order.getAircraftSn());
                item.put("aircraft_name", order.getAircraftName());
                item.put("wayline_uuid", order.getWaylineUuid());
                item.put("wayline_name", order.getWaylineName());
                item.put("alarm_image_url", order.getAlarmImagePath() != null ? "/api/zhimou-img/" + order.getAlarmImagePath() : null);
                item.put("handler_email", order.getHandlerEmail());
                item.put("handler_name", order.getHandlerName());
                item.put("status", order.getStatus());
                item.put("status_text", getStatusText(order.getStatus()));
                item.put("handle_comment", order.getHandleComment());
                item.put("reject_reason", order.getRejectReason());
                item.put("latitude", order.getLatitude());
                item.put("longitude", order.getLongitude());
                item.put("event_time", order.getEventTime());
                item.put("submit_time", order.getSubmitTime());
                item.put("complete_time", order.getCompleteTime());
                item.put("reject_time", order.getRejectTime());
                item.put("create_time", order.getCreateTime());

                List<WorkOrderImage> images = workOrderImageMapper.selectList(
                        new LambdaQueryWrapper<WorkOrderImage>()
                                .eq(WorkOrderImage::getWorkOrderId, order.getId())
                                .eq(WorkOrderImage::getImageType, "HANDLER"));
                List<String> handlerImageUrls = new ArrayList<>();
                for (WorkOrderImage img : images) {
                    handlerImageUrls.add("/api/zhimou-img/" + img.getImagePath());
                }
                item.put("handler_images", handlerImageUrls);
                list.add(item);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", result.getTotal());
            data.put("page", page);
            data.put("size", size);
            return R.ok(data);
        } catch (Exception e) {
            log.error("[工单] 查询工单列表失败", e);
            return R.fail("查询失败");
        }
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getWorkOrder(@PathVariable Long id) {
        try {
            WorkOrder workOrder = workOrderMapper.selectById(id);
            if (workOrder == null || workOrder.getDeleted() == 1) {
                return R.fail("工单不存在");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", workOrder.getId());
            data.put("code", workOrder.getCode());
            data.put("alarm_id", workOrder.getAlarmId());
            data.put("alarm_type", workOrder.getAlarmType());
            data.put("aircraft_sn", workOrder.getAircraftSn());
            data.put("aircraft_name", workOrder.getAircraftName());
            data.put("wayline_uuid", workOrder.getWaylineUuid());
            data.put("wayline_name", workOrder.getWaylineName());
            data.put("alarm_image_url", workOrder.getAlarmImagePath() != null ? "/api/zhimou-img/" + workOrder.getAlarmImagePath() : null);
            data.put("handler_email", workOrder.getHandlerEmail());
            data.put("handler_name", workOrder.getHandlerName());
            data.put("h5_token", workOrder.getH5Token());
            data.put("status", workOrder.getStatus());
            data.put("status_text", getStatusText(workOrder.getStatus()));
            data.put("handle_comment", workOrder.getHandleComment());
            data.put("reject_reason", workOrder.getRejectReason());
            data.put("cc_email", workOrder.getCcEmail());
            data.put("latitude", workOrder.getLatitude());
            data.put("longitude", workOrder.getLongitude());
            data.put("event_time", workOrder.getEventTime());
            data.put("submit_time", workOrder.getSubmitTime());
            data.put("complete_time", workOrder.getCompleteTime());
            data.put("reject_time", workOrder.getRejectTime());
            data.put("create_time", workOrder.getCreateTime());

            List<WorkOrderImage> images = workOrderImageMapper.selectList(
                    new LambdaQueryWrapper<WorkOrderImage>()
                            .eq(WorkOrderImage::getWorkOrderId, workOrder.getId())
                            .eq(WorkOrderImage::getImageType, "HANDLER"));
            List<String> handlerImageUrls = new ArrayList<>();
            for (WorkOrderImage img : images) {
                handlerImageUrls.add("/api/zhimou-img/" + img.getImagePath());
            }
            data.put("handler_images", handlerImageUrls);

            return R.ok(data);
        } catch (Exception e) {
            log.error("[工单] 查询工单详情失败", e);
            return R.fail("查询失败");
        }
    }

    @PostMapping("/{id}/complete")
    public R<Map<String, Object>> completeWorkOrder(@PathVariable Long id) {
        try {
            WorkOrder workOrder = workOrderMapper.selectById(id);
            if (workOrder == null || workOrder.getDeleted() == 1) {
                return R.fail("工单不存在");
            }

            if ("COMPLETED".equals(workOrder.getStatus())) {
                return R.fail("工单已完成");
            }

            workOrder.setStatus("COMPLETED");
            workOrder.setCompleteTime(LocalDateTime.now());
            workOrderMapper.updateById(workOrder);

            log.info("[工单] 工单完成, work_order_id={}, code={}", id, workOrder.getCode());
            return R.ok("工单已完成", null);
        } catch (Exception e) {
            log.error("[工单] 完成工单失败", e);
            return R.fail("操作失败");
        }
    }

    @PostMapping("/{id}/reject")
    public R<Map<String, Object>> rejectWorkOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            String rejectReason = body.get("reject_reason") != null ? String.valueOf(body.get("reject_reason")) : null;
            String ccEmail = body.get("cc_email") != null ? String.valueOf(body.get("cc_email")) : null;

            WorkOrder workOrder = workOrderMapper.selectById(id);
            if (workOrder == null || workOrder.getDeleted() == 1) {
                return R.fail("工单不存在");
            }

            if ("REJECTED".equals(workOrder.getStatus())) {
                return R.fail("工单已驳回");
            }

            workOrder.setStatus("REJECTED");
            workOrder.setRejectReason(rejectReason);
            workOrder.setCcEmail(ccEmail);
            workOrder.setRejectTime(LocalDateTime.now());
            workOrderMapper.updateById(workOrder);

            if (mailService.isConfigured()) {
                if (StrUtil.isNotBlank(workOrder.getHandlerEmail())) {
                    mailService.sendRejectEmail(workOrder.getHandlerEmail(), workOrder.getCode(), rejectReason, workOrder.getH5Token());
                }
                if (StrUtil.isNotBlank(ccEmail)) {
                    mailService.sendRejectEmail(ccEmail, workOrder.getCode(), rejectReason, workOrder.getH5Token());
                }
            }

            log.info("[工单] 工单驳回, work_order_id={}, code={}", id, workOrder.getCode());
            return R.ok("工单已驳回", null);
        } catch (Exception e) {
            log.error("[工单] 驳回工单失败", e);
            return R.fail("操作失败");
        }
    }

    @GetMapping("/h5/{token}")
    public R<Map<String, Object>> getWorkOrderByToken(@PathVariable String token) {
        try {
            WorkOrder workOrder = workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getH5Token, token)
                    .eq(WorkOrder::getDeleted, 0));

            if (workOrder == null) {
                return R.fail("工单不存在或已过期");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", workOrder.getId());
            data.put("code", workOrder.getCode());
            data.put("alarm_type", workOrder.getAlarmType());
            data.put("aircraft_sn", workOrder.getAircraftSn());
            data.put("aircraft_name", workOrder.getAircraftName());
            data.put("wayline_name", workOrder.getWaylineName());
            data.put("alarm_image_url", workOrder.getAlarmImagePath() != null ? "/api/zhimou-img/" + workOrder.getAlarmImagePath() : null);
            data.put("handler_name", workOrder.getHandlerName());
            data.put("status", workOrder.getStatus());
            data.put("status_text", getStatusText(workOrder.getStatus()));
            data.put("latitude", workOrder.getLatitude());
            data.put("longitude", workOrder.getLongitude());
            data.put("event_time", workOrder.getEventTime());
            data.put("create_time", workOrder.getCreateTime());

            List<WorkOrderImage> images = workOrderImageMapper.selectList(
                    new LambdaQueryWrapper<WorkOrderImage>()
                            .eq(WorkOrderImage::getWorkOrderId, workOrder.getId())
                            .eq(WorkOrderImage::getImageType, "HANDLER"));
            List<String> handlerImageUrls = new ArrayList<>();
            for (WorkOrderImage img : images) {
                handlerImageUrls.add("/api/zhimou-img/" + img.getImagePath());
            }
            data.put("handler_images", handlerImageUrls);

            return R.ok(data);
        } catch (Exception e) {
            log.error("[工单] H5查询工单失败, token={}", token, e);
            return R.fail("查询失败");
        }
    }

    @PostMapping("/h5/upload")
    public R<Map<String, Object>> uploadH5Image(@RequestParam("file") MultipartFile file,
                                                @RequestParam("work_order_id") Long workOrderId) {
        try {
            WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
            if (workOrder == null || workOrder.getDeleted() == 1) {
                return R.fail("工单不存在");
            }

            if (file.isEmpty()) {
                return R.fail("请选择图片");
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = System.currentTimeMillis() + "_" + IdUtil.fastSimpleUUID().substring(0, 8) + extension;
            String relativePath = dateStr + "/" + fileName;
            String savePath = imageSaveRootDir + "/" + relativePath;

            File saveFile = new File(savePath);
            if (!saveFile.getParentFile().exists()) {
                saveFile.getParentFile().mkdirs();
            }

            file.transferTo(saveFile);

            WorkOrderImage workOrderImage = new WorkOrderImage();
            workOrderImage.setWorkOrderId(workOrderId);
            workOrderImage.setImagePath(relativePath);
            workOrderImage.setImageType("HANDLER");
            workOrderImageMapper.insert(workOrderImage);

            Map<String, Object> data = new HashMap<>();
            data.put("image_url", "/api/zhimou-img/" + relativePath);
            data.put("image_path", relativePath);

            log.info("[工单] H5上传图片成功, work_order_id={}, path={}", workOrderId, relativePath);
            return R.ok("上传成功", data);
        } catch (IOException e) {
            log.error("[工单] H5上传图片失败", e);
            return R.fail("上传失败");
        }
    }

    @PostMapping("/h5/submit")
    public R<Map<String, Object>> submitWorkOrder(@RequestBody Map<String, Object> body) {
        try {
            Long workOrderId = body.get("work_order_id") != null ? Long.valueOf(String.valueOf(body.get("work_order_id"))) : null;
            String handleComment = body.get("handle_comment") != null ? String.valueOf(body.get("handle_comment")) : null;

            if (workOrderId == null) {
                return R.fail("work_order_id 必填");
            }

            WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
            if (workOrder == null || workOrder.getDeleted() == 1) {
                return R.fail("工单不存在");
            }

            if ("SUBMITTED".equals(workOrder.getStatus()) || "COMPLETED".equals(workOrder.getStatus())) {
                return R.fail("工单状态不允许提交");
            }

            workOrder.setStatus("SUBMITTED");
            workOrder.setHandleComment(handleComment);
            workOrder.setSubmitTime(LocalDateTime.now());
            workOrderMapper.updateById(workOrder);

            log.info("[工单] H5提交工单, work_order_id={}, code={}", workOrderId, workOrder.getCode());
            return R.ok("提交成功", null);
        } catch (Exception e) {
            log.error("[工单] H5提交工单失败", e);
            return R.fail("提交失败");
        }
    }

    @PostMapping("/test-mail")
    public R<Map<String, Object>> testMail(@RequestBody Map<String, String> body) {
        try {
            String toEmail = body.get("to_email");
            if (StrUtil.isBlank(toEmail)) {
                return R.fail("请提供接收邮箱");
            }

            log.info("[邮件测试] 发送测试邮件到: {}", toEmail);
            mailService.sendWorkOrderEmail(toEmail, "测试人员", "TEST001", "测试告警", "test-token-123");

            return R.ok("测试邮件已发送", null);
        } catch (Exception e) {
            log.error("[邮件测试] 发送失败", e);
            return R.fail("发送失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        try {
            WorkOrder workOrder = workOrderMapper.selectById(id);
            if (workOrder == null) {
                return R.fail("工单不存在");
            }

            workOrderMapper.deleteById(id);

            workOrderImageMapper.delete(new LambdaQueryWrapper<WorkOrderImage>()
                    .eq(WorkOrderImage::getWorkOrderId, id));

            log.info("[工单] 删除工单, id={}, code={}", id, workOrder.getCode());
            return R.ok("删除成功", null);
        } catch (Exception e) {
            log.error("[工单] 删除工单失败", e);
            return R.fail("删除失败: " + e.getMessage());
        }
    }

    private String generateWorkOrderCode() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String getStatusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "PENDING": return "待处理";
            case "PROCESSING": return "处理中";
            case "SUBMITTED": return "已提交";
            case "COMPLETED": return "已完成";
            case "REJECTED": return "已驳回";
            default: return status;
        }
    }
}
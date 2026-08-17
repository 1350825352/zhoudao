package com.dji.sikong.service;

import com.dji.sikong.config.CompanyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Slf4j
@Service
public class MailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${h5.host:localhost}")
    private String h5Host;

    @Value("${h5.port:3203}")
    private String h5Port;

    @Value("${h5.context-path:}")
    private String h5ContextPath;

    @Resource
    private CompanyConfig companyConfig;

    public void sendWorkOrderEmail(String toEmail, String handlerName, String workOrderCode,
                                   String alarmType, String h5Token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("【工单通知】新工单待处理");

            String portPart = "80".equals(h5Port) ? "" : ":" + h5Port;
            String pathPart = h5ContextPath.isEmpty() ? "/" : "/" + h5ContextPath.replaceAll("^/|/$", "") + "/";
            String h5Url = "http://" + h5Host + portPart + pathPart + workOrderCode + "?token=" + h5Token;

            String htmlContent = "<!DOCTYPE html>" +
                    "<html lang=\"zh-CN\">" +
                    "<head><meta charset=\"UTF-8\"><title>工单通知</title></head>" +
                    "<body style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">" +
                    "<div style=\"background: linear-gradient(135deg, #409eff, #66b1ff); padding: 25px; border-radius: 12px; color: white;\">" +
                    "<h1 style=\"margin: 0; font-size: 22px;\">🔔 新工单待处理</h1>" +
                    "<p style=\"margin: 10px 0 0; font-size: 14px; opacity: 0.9;\">" + alarmType + "</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px;\">" +
                    "<p style=\"font-size: 15px; line-height: 1.8; color: #333;\">您好！这是来自" + companyConfig.getName() + "的" + alarmType + "工单，请点击下方链接处理：</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px; padding: 15px; background: #f0f5ff; border-radius: 8px; border: 1px solid #e6f0ff;\">" +
                    "<a href=\"" + h5Url + "\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"font-size: 14px; color: #409eff; word-break: break-all; text-decoration: none;\">" + h5Url + "</a>" +
                    "</div>" +
                    "<div style=\"margin-top: 15px; padding: 15px; background: #fff7e6; border-radius: 8px; border-left: 4px solid #e6a23c;\">" +
                    "<p style=\"margin: 0; font-size: 14px; color: #8a6d3b;\">📌 点击链接后，请选择「浏览器」打开，以便正常使用拍照功能</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px; padding: 15px; background: #f8f9fa; border-radius: 8px;\">" +
                    "<p style=\"margin: 5px 0; font-size: 14px; color: #666;\"><strong>工单编号：</strong>" + workOrderCode + "</p>" +
                    "<p style=\"margin: 5px 0; font-size: 14px; color: #666;\"><strong>告警类型：</strong>" + alarmType + "</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px; color: #909399; font-size: 12px;\">" +
                    "<p>此邮件由系统自动发送，请勿回复。</p>" +
                    "</div>" +
                    "</body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("[邮件] 工单通知邮件发送成功, to={}, workOrderCode={}", toEmail, workOrderCode);
        } catch (MessagingException e) {
            log.error("[邮件] 发送邮件失败", e);
        }
    }

    public void sendRejectEmail(String toEmail, String workOrderCode, String rejectReason, String h5Token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject("【工单通知】工单已驳回");

            String portPart = "80".equals(h5Port) ? "" : ":" + h5Port;
            String pathPart = h5ContextPath.isEmpty() ? "/" : "/" + h5ContextPath.replaceAll("^/|/$", "") + "/";
            String h5Url = "http://" + h5Host + portPart + pathPart + workOrderCode + "?token=" + h5Token;

            String htmlContent = "<!DOCTYPE html>" +
                    "<html lang=\"zh-CN\">" +
                    "<head><meta charset=\"UTF-8\"><title>工单驳回通知</title></head>" +
                    "<body style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">" +
                    "<div style=\"background: linear-gradient(135deg, #f56c6c, #f78989); padding: 20px; border-radius: 8px; color: white;\">" +
                    "<h1 style=\"margin: 0; font-size: 24px;\">工单驳回通知</h1>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px;\">" +
                    "<p style=\"font-size: 15px; line-height: 1.8; color: #333;\">您好！您处理的工单已被驳回，请点击下方链接重新处理：</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px; padding: 15px; background: #fff7e6; border-radius: 8px; border: 1px solid #ffeeba;\">" +
                    "<a href=\"" + h5Url + "\" target=\"_blank\" rel=\"noopener noreferrer\" style=\"font-size: 14px; color: #e6a23c; word-break: break-all; text-decoration: none;\">" + h5Url + "</a>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px; padding: 20px; background: #f8f9fa; border-radius: 8px;\">" +
                    "<p style=\"margin: 10px 0;\"><strong>工单编号：</strong>" + workOrderCode + "</p>" +
                    "<p style=\"margin: 10px 0;\"><strong>驳回原因：</strong>" + rejectReason + "</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 15px; padding: 15px; background: #f0f5ff; border-radius: 8px; border-left: 4px solid #409eff;\">" +
                    "<p style=\"margin: 0; font-size: 14px; color: #606266;\">📌 点击链接后，请选择「浏览器」打开，以便正常使用拍照功能</p>" +
                    "</div>" +
                    "<div style=\"margin-top: 20px; color: #909399; font-size: 12px;\">" +
                    "<p>此邮件由系统自动发送，请勿回复。</p>" +
                    "</div>" +
                    "</body></html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("[邮件] 工单驳回邮件发送成功, to={}, workOrderCode={}", toEmail, workOrderCode);
        } catch (MessagingException e) {
            log.error("[邮件] 发送邮件失败", e);
        }
    }

    public boolean isConfigured() {
        return senderEmail != null && !senderEmail.isEmpty() && !"your_email@qq.com".equals(senderEmail);
    }
}
package com.dji.sikong.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.io.File;

/**
 * 知眸AI识别图片 静态资源映射:
 * 把 /zhimou-img/** 开头的URL 映射到 本地磁盘的 image.save-root-dir
 * 让前端浏览器能通过 HTTP 访问本地保存的图片
 */
@Slf4j
@Configuration
public class ZhimouWebMvcConfig implements WebMvcConfigurer {

    @Resource
    private ZhimouProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            String saveRoot = properties.getImage().getSaveRootDir();
            String urlPrefix = properties.getImage().getUrlPrefix();
            if (saveRoot == null || urlPrefix == null) return;

            // 规范化urlPrefix: /zhimou-img -> /zhimou-img/**
            String pattern = urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**";
            if (!pattern.startsWith("/")) pattern = "/" + pattern;

            // 规范化本地路径: D:/zhimou-images -> file:D:/zhimou-images/
            String location = saveRoot.endsWith("/") || saveRoot.endsWith("\\") ? saveRoot : saveRoot + File.separator;
            if (!location.toLowerCase().startsWith("file:")) {
                location = "file:" + location;
            }

            log.info("[知眸-静态资源] 映射 URL模式:{}  ->  本地目录:{}", pattern, location);
            registry.addResourceHandler(pattern).addResourceLocations(location);

            // 顺手确保目录存在
            File dir = new File(saveRoot);
            if (!dir.exists()) {
                boolean ok = dir.mkdirs();
                log.info("[知眸-静态资源] 目录不存在, 自动创建:{} -> {}", saveRoot, ok);
            }
        } catch (Exception e) {
            log.error("[知眸-静态资源映射] 配置异常", e);
        }

        // ================ 二期照片识别专用目录映射 ================
        // /image-detect-img/** -> {detectRootDir}/ (结构: yyyyMMdd/{taskUuid}/original|annotated/xx.jpg)
        try {
            String detectRoot = properties.getImage().getDetectRootDir();
            if (detectRoot != null && !detectRoot.trim().isEmpty()) {
                String location = detectRoot.endsWith("/") || detectRoot.endsWith("\\") ? detectRoot : detectRoot + File.separator;
                if (!location.toLowerCase().startsWith("file:")) {
                    location = "file:" + location;
                }
                log.info("[知眸-静态资源] 映射 URL模式:/image-detect-img/**  ->  本地目录:{}", location);
                registry.addResourceHandler("/image-detect-img/**").addResourceLocations(location);

                File ddir = new File(detectRoot);
                if (!ddir.exists()) {
                    boolean ok = ddir.mkdirs();
                    log.info("[知眸-静态资源] 二期目录不存在, 自动创建:{} -> {}", detectRoot, ok);
                }
            }
        } catch (Exception e) {
            log.error("[知眸-静态资源映射] 二期目录配置异常", e);
        }
    }
}

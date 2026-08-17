package com.dji.sikong.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "zhimou")
public class ZhimouProperties {

    private OpenApi openapi = new OpenApi();

    private ImageConfig image = new ImageConfig();

    /**
     * 飞行位置上报间隔(毫秒), 0表示OSD事件触发即上报
     */
    private long flightReportIntervalMs = 1000L;

    /**
     * 通用工具: 把逗号/分号/空格分隔的app_id字符串解析成去重后的非空List
     * 支持: "id1" | "id1,id2" | "id1;id2" | "id1 id2" | "[id1,id2]" 多种格式
     */
    public static List<String> parseAppIds(String raw) {
        if (StrUtil.isBlank(raw)) return Collections.emptyList();
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            try { s = s.substring(1, s.length() - 1); } catch (Exception ignore) {}
        }
        List<String> all = new ArrayList<>();
        for (String part : s.split("[,;，；\\s]+")) {
            String p = part == null ? null : part.trim();
            if (StrUtil.isNotBlank(p) && !all.contains(p)) all.add(p);
        }
        return all;
    }

    @Data
    public static class OpenApi {
        /**
         * 知眸OpenAPI基础地址, 如 https://openapi.zhimouyun.com
         */
        private String baseUrl = "https://openapi.zhimouyun.com";

        /**
         * 应用凭证-appkey
         */
        private String appkey;

        /**
         * 应用凭证-appsecret
         */
        private String appsecret;

        /**
         * 知眸回调我们SpringBoot服务的完整HTTP地址
         * (需知眸服务能网络访问到, 需要带context-path: /api)
         */
        private String callbackUrl;

        /**
         * (可选) AI输出标注视频的推流地址模板, 支持占位符 {task_id}
         * 若不填, 创建知眸任务时不传 output, 不生成AI叠加标注的RTMP流
         */
        private String outputUrlTemplate;

        /**
         * (可选) 自动开播时默认使用的知眸应用ID(飞机起飞自动创建AI视频任务时使用)
         * ✅支持多应用: "id1,id2,id3" 逗号分隔, 同时开多个算法
         * 若不填: 需要前端手动选应用后开播才能创建知眸任务
         */
        private String defaultAppId;

        /**
         * 便捷方法: 把defaultAppId解析成List(天然支持逗号分隔多ID)
         */
        public List<String> getDefaultAppIdList() {
            return ZhimouProperties.parseAppIds(this.defaultAppId);
        }

        /**
         * (可选) 自动开播时默认使用的知眸应用包ID
         */
        private String defaultPkgId;
    }

    @Data
    public static class ImageConfig {
        /**
         * 本地磁盘保存根目录. 如: D:/zhimou-images  或  /data/zhimou-images
         * 注意: 不要配置到jar包内 (如 resources/static), jar包无法写入!
         * TRAE本地调试: 推荐放在项目目录下(如 H:/traeproject/ceshisikongapiAI/zhimou-images), 确保在IDE沙盒可写白名单内
         * 生产部署: 可改为 D:/zhimou-images 或任意独立磁盘路径(需确保启动账号有写权限)
         */
        private String saveRootDir = "H:/traeproject/ceshisikongapiAI/zhimou-images";

        /**
         * 静态资源URL前缀(配合WebMvcConfigurer静态映射).
         * 默认 /zhimou-img/ -> 最终前端访问 /api/zhimou-img/yyyyMMdd/xxx.png
         */
        private String urlPrefix = "/zhimou-img/";
    }
}

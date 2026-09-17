package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.config.DjiMediaProperties;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.mapper.LiveStreamMapper;
import com.dji.sikong.service.MediaServerStreamProbeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MediaServerStreamProbeServiceImpl implements MediaServerStreamProbeService {

    @Resource
    private DjiMediaProperties mediaProperties;

    @Resource
    private LiveStreamMapper liveStreamMapper;

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    @Override
    public boolean isAircraftStreaming(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return false;
        List<String> streamKeys = buildCandidateStreamKeys(aircraftSn);
        if (streamKeys.isEmpty()) return false;
        DjiMediaProperties.StreamServer ss = mediaProperties.getStreamServer();
        String secret = extractSecret(ss.getRtmpUrl());
        for (String sk : streamKeys) {
            String internalTpl = ss.getPullUrlTemplateInternal();
            if (StrUtil.isNotBlank(internalTpl)) {
                String url = buildPullUrl(internalTpl, sk, secret);
                if (probeHasFlv(url)) return true;
            }
            String publicTpl = ss.getPullUrlTemplate();
            if (StrUtil.isNotBlank(publicTpl)) {
                String url = buildPullUrl(publicTpl, sk, secret);
                if (probeHasFlv(url)) return true;
            }
        }
        return false;
    }

    @Override
    public String buildHttpFlvPullUrl(LiveStream liveStream) {
        if (liveStream == null) return null;
        DjiMediaProperties.StreamServer ss = mediaProperties.getStreamServer();
        if (ss == null) return null;

        // 从pushUrl中提取streamKey和secret
        String streamKey = extractStreamKey(liveStream.getPushUrl());
        String secret = extractSecret(liveStream.getPushUrl());
        if (StrUtil.isBlank(secret)) secret = extractSecret(ss.getRtmpUrl());

        // 优先内网, 其次外网
        String tpl = ss.getPullUrlTemplateInternal();
        if (StrUtil.isBlank(tpl)) tpl = ss.getPullUrlTemplate();
        if (StrUtil.isBlank(tpl) || StrUtil.isBlank(streamKey)) return null;

        return buildPullUrl(tpl, streamKey, secret);
    }

    @Override
    public boolean isStreamPlayable(String httpFlvUrl) {
        return probeHasFlv(httpFlvUrl);
    }

    @Override
    public String probePlayableHttpFlvUrl(LiveStream liveStream) {
        if (liveStream == null) return null;
        DjiMediaProperties.StreamServer ss = mediaProperties.getStreamServer();
        if (ss == null) return null;

        String streamKey = extractStreamKey(liveStream.getPushUrl());
        String secret = extractSecret(liveStream.getPushUrl());
        if (StrUtil.isBlank(secret)) secret = extractSecret(ss.getRtmpUrl());
        if (StrUtil.isBlank(streamKey)) return null;

        // 先试内网
        String internalTpl = ss.getPullUrlTemplateInternal();
        if (StrUtil.isNotBlank(internalTpl)) {
            String url = buildPullUrl(internalTpl, streamKey, secret);
            if (probeHasFlv(url)) {
                System.out.println("[流探测-内网可播放✅] url=" + url);
                return url;
            } else {
                System.out.println("[流探测-内网不可播放❌] url=" + url);
            }
        }

        // 再试外网
        String publicTpl = ss.getPullUrlTemplate();
        if (StrUtil.isNotBlank(publicTpl)) {
            String url = buildPullUrl(publicTpl, streamKey, secret);
            if (probeHasFlv(url)) {
                System.out.println("[流探测-外网可播放✅] url=" + url);
                return url;
            } else {
                System.out.println("[流探测-外网不可播放❌] url=" + url);
            }
        }

        return null;
    }

    @Override
    public String probePlayableHttpFlvUrl(LiveStream liveStream, boolean internal) {
        if (liveStream == null) return null;
        DjiMediaProperties.StreamServer ss = mediaProperties.getStreamServer();
        if (ss == null) return null;

        String streamKey = extractStreamKey(liveStream.getPushUrl());
        String secret = extractSecret(liveStream.getPushUrl());
        if (StrUtil.isBlank(secret)) secret = extractSecret(ss.getRtmpUrl());
        if (StrUtil.isBlank(streamKey)) return null;

        String tpl = internal ? ss.getPullUrlTemplateInternal() : ss.getPullUrlTemplate();
        if (StrUtil.isNotBlank(tpl)) {
            String url = buildPullUrl(tpl, streamKey, secret);
            if (probeHasFlv(url)) {
                System.out.println("[流探测-" + (internal ? "内网" : "外网") + "可播放✅] url=" + url);
                return url;
            } else {
                System.out.println("[流探测-" + (internal ? "内网" : "外网") + "不可播放❌] url=" + url);
            }
        }
        return null;
    }

    @Override
    public String buildAiRecognitionPullUrl(LiveStream liveStream, boolean internal) {
        if (liveStream == null) return null;
        DjiMediaProperties.StreamServer ss = mediaProperties.getStreamServer();
        if (ss == null) return null;

        String streamKey = extractStreamKey(liveStream.getPushUrl());
        String secret = extractSecret(liveStream.getPushUrl());
        if (StrUtil.isBlank(secret)) secret = extractSecret(ss.getRtmpUrl());
        if (StrUtil.isBlank(streamKey)) return null;

        if (internal) {
            // 内网：从 rtmp-url-ai 推导
            String rtmpUrlAi = ss.getRtmpUrlAi();
            if (StrUtil.isBlank(rtmpUrlAi)) return null;
            String aiRtmp = rtmpUrlAi.replace("{streamKey}", streamKey).replace("{secret}", secret);
            try {
                int qIdx = aiRtmp.indexOf('?');
                String path = qIdx >= 0 ? aiRtmp.substring(0, qIdx) : aiRtmp;
                String query = qIdx >= 0 ? aiRtmp.substring(qIdx + 1) : null;
                java.net.URI uri = new java.net.URI(path);
                String host = uri.getHost();
                StringBuilder httpBase = new StringBuilder("http://").append(host).append(":2022");
                if (StrUtil.isNotBlank(uri.getPath())) httpBase.append(uri.getPath());
                if (!httpBase.toString().endsWith(".flv")) httpBase.append(".flv");
                if (StrUtil.isNotBlank(query)) httpBase.append('?').append(query);
                return httpBase.toString();
            } catch (Exception e) {
                String http = aiRtmp.replaceFirst("^rtmp://", "http://");
                int qIdx2 = http.indexOf('?');
                if (qIdx2 >= 0) return http.substring(0, qIdx2) + ".flv" + http.substring(qIdx2);
                return http + ".flv";
            }
        } else {
            // 外网：从 pull-url-template-ai 构建
            String tpl = ss.getPullUrlTemplateAi();
            log.info("[识别流-外网构建] tpl={}, streamKey={}, secret={}", tpl, streamKey, secret == null ? "null" : "有");
            if (StrUtil.isBlank(tpl)) return null;
            return buildPullUrl(tpl, streamKey, secret);
        }
    }

    private List<String> buildCandidateStreamKeys(String aircraftSn) {
        List<String> result = new ArrayList<>(4);
        try {
            LiveStream latest = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                    .eq(LiveStream::getAircraftSn, aircraftSn)
                    .eq(LiveStream::getDeleted, 0)
                    .orderByDesc(LiveStream::getId)
                    .last(" LIMIT 1"));
            if (latest != null && StrUtil.isNotBlank(latest.getCameraIndex())) {
                result.add(aircraftSn + "/" + latest.getCameraIndex());
            }
        } catch (Exception ignore) {}
        result.add(aircraftSn + "/99-0-0");
        result.add(aircraftSn + "/0");
        result.add(aircraftSn);
        return result;
    }

    /** 从pushUrl中提取streamKey, 即 /live/ 后面到 ? 之前的部分 */
    private String extractStreamKey(String pushUrl) {
        if (StrUtil.isBlank(pushUrl)) return "";
        int pLive = pushUrl.indexOf("/live/");
        if (pLive < 0) return "";
        int pQ = pushUrl.indexOf('?', pLive);
        return (pQ > pLive) ? pushUrl.substring(pLive + 6, pQ) : pushUrl.substring(pLive + 6);
    }

    private String extractSecret(String rtmpUrl) {
        if (StrUtil.isBlank(rtmpUrl)) return "";
        int q = rtmpUrl.indexOf('?');
        if (q < 0) return "";
        String qs = rtmpUrl.substring(q + 1);
        for (String pair : qs.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if ("secret".equalsIgnoreCase(k)) {
                return eq >= 0 ? pair.substring(eq + 1) : "";
            }
        }
        return "";
    }

    private String buildPullUrl(String tpl, String streamKey, String secret) {
        return tpl.replace("{streamKey}", streamKey)
                .replace("{streamkey}", streamKey)
                .replace("{secret}", StrUtil.nullToDefault(secret, ""));
    }

    private boolean probeHasFlv(String url) {
        if (StrUtil.isBlank(url)) return false;
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            // 不加Range头，直接GET请求
            int status = conn.getResponseCode();
            String ct = conn.getContentType();
            // 先检查状态码和Content-Type
            boolean headerOk = (status == 200 || status == 206)
                    && StrUtil.isNotBlank(ct)
                    && (ct.contains("flv") || ct.contains("video") || ct.contains("octet-stream"));
            if (!headerOk) {
                System.out.println("[流探测-DEBUG] url=" + url
                        + " status=" + status
                        + " contentType=" + ct
                        + " 可播放=false (Header不匹配)");
                return false;
            }
            // 再读取前3个字节验证是否是FLV文件头
            is = conn.getInputStream();
            byte[] header = new byte[3];
            int read = is.read(header);
            boolean isFlv = (read >= 3) && header[0] == 'F' && header[1] == 'L' && header[2] == 'V';
            System.out.println("[流探测-DEBUG] url=" + url
                    + " status=" + status
                    + " contentType=" + ct
                    + " FLV头=" + (isFlv ? "是" : "否")
                    + " 可播放=" + isFlv);
            return isFlv;
        } catch (Exception e) {
            System.out.println("[流探测-异常] url=" + url + " err=" + e.getMessage());
            return false;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignore) {}
            }
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignore) {}
            }
        }
    }
}

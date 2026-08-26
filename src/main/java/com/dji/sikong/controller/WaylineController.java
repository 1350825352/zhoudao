package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.service.OrganizationProjectService;
import com.dji.sikong.service.WaylineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/wayline")
@CrossOrigin(origins = "*")
public class WaylineController {

    @Resource
    private WaylineService waylineService;

    @Resource
    private OrganizationProjectService projectService;

    // ====================================================
    // 需求2：手动同步航线数据(从司空2 OpenAPI拉取)
    // ====================================================

    /**
     * 前端点击[同步航线]按钮调用
     * @param projectUuid 可选，不传则同步全部项目的航线
     */
    @PostMapping("/sync")
    public R<List<Wayline>> syncWaylines(
            @RequestParam(required = false) String projectUuid) {
        // 先确保有项目数据
        if (projectService.list(new LambdaQueryWrapper<>()).isEmpty()) {
            projectService.syncFromOpenApi();
        }
        List<Wayline> list = waylineService.syncFromOpenApi(projectUuid);
        return R.ok("同步完成，共" + list.size() + "条航线", list);
    }

    // ====================================================
    // 航线CRUD
    // ====================================================

    @GetMapping("/list")
    public R<List<Wayline>> list(@RequestParam(required = false) String projectUuid) {
        return R.ok(waylineService.listByProject(projectUuid));
    }

    @GetMapping("/page")
    public R<Page<Wayline>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String projectUuid,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Wayline> wrapper = new LambdaQueryWrapper<Wayline>().eq(Wayline::getDeleted, 0);
        if (projectUuid != null) wrapper.eq(Wayline::getProjectUuid, projectUuid);
        if (keyword != null) wrapper.like(Wayline::getWaylineName, keyword);
        wrapper.orderByDesc(Wayline::getUpdateTime);
        return R.ok(waylineService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/{id}")
    public R<Wayline> get(@PathVariable Long id) {
        return R.ok(waylineService.getById(id));
    }

    @GetMapping("/uuid/{uuid}")
    public R<Wayline> getByUuid(@PathVariable String uuid) {
        return R.ok(waylineService.getByUuid(uuid));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody Wayline wayline) {
        if (wayline.getSyncSource() == null) wayline.setSyncSource(2);
        return R.ok(waylineService.saveOrUpdate(wayline));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(waylineService.removeById(id));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadWaylineFile(@RequestParam String url) {
        return doDownload(url);
    }

    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadWaylineFilePost(@RequestBody java.util.Map<String, String> body) {
        return doDownload(body.get("url"));
    }

    private ResponseEntity<byte[]> doDownload(String url) {
        try {
            log.info("[航线下载] 开始下载航线文件: {}", url);
            URL downloadUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            // 避免CDN缓存返回旧航线文件(航线同步后内容可能变了但URL没变)
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Pragma", "no-cache");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error("[航线下载] 下载失败，HTTP状态码: {}", responseCode);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
            
            InputStream inputStream = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, n);
            }
            byte[] bytes = buffer.toByteArray();
            inputStream.close();
            conn.disconnect();
            
            log.info("[航线下载] 下载成功，文件大小: {} bytes", bytes.length);
            
            HttpHeaders headers = new HttpHeaders();
            String contentType = conn.getContentType();
            if (contentType == null) contentType = "application/octet-stream";
            headers.set("Content-Type", contentType);
            
            String fileName = "wayline.kmz";
            String contentDisposition = conn.getHeaderField("Content-Disposition");
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                int idx = contentDisposition.indexOf("filename=");
                fileName = contentDisposition.substring(idx + 9);
                if (fileName.startsWith("\"")) fileName = fileName.substring(1, fileName.length() - 1);
            }
            headers.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(bytes);
                    
        } catch (Exception e) {
            log.error("[航线下载] 下载失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}

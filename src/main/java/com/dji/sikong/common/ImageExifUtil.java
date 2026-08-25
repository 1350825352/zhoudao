package com.dji.sikong.common;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 图片EXIF元数据工具类
 * 主要用途: 从无人机航拍照片中提取GPS经纬度信息
 * 依赖: com.drewnoakes:metadata-extractor
 */
@Slf4j
public class ImageExifUtil {

    /**
     * 从图片文件提取GPS经纬度
     *
     * @param imageFile 图片文件
     * @return [纬度, 经度, 海拔], 任一为null表示该维度无数据; 全null表示无GPS信息
     */
    public static BigDecimal[] extractGps(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            return new BigDecimal[]{null, null, null};
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
            return extractGpsFromMetadata(metadata);
        } catch (Exception e) {
            log.warn("[EXIF] 读取GPS失败 file={}, err={}", imageFile.getName(), e.getMessage());
            return new BigDecimal[]{null, null, null};
        }
    }

    /**
     * 从图片字节流提取GPS经纬度
     *
     * @param inputStream 图片输入流
     * @return [纬度, 经度, 海拔]
     */
    public static BigDecimal[] extractGps(InputStream inputStream) {
        if (inputStream == null) {
            return new BigDecimal[]{null, null, null};
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            return extractGpsFromMetadata(metadata);
        } catch (Exception e) {
            log.warn("[EXIF] 读取GPS失败 err={}", e.getMessage());
            return new BigDecimal[]{null, null, null};
        }
    }

    /**
     * 从Metadata中提取GPS经纬度和海拔
     */
    private static BigDecimal[] extractGpsFromMetadata(Metadata metadata) {
        BigDecimal latitude = null;
        BigDecimal longitude = null;
        BigDecimal altitude = null;

        // 优先用 GpsDirectory 的 GeoLocation (含南北/东西半球处理)
        for (Directory dir : metadata.getDirectoriesOfType(GpsDirectory.class)) {
            if (dir.containsTag(GpsDirectory.TAG_LATITUDE) && dir.containsTag(GpsDirectory.TAG_LATITUDE_REF)) {
                GeoLocation loc = ((GpsDirectory) dir).getGeoLocation();
                if (loc != null && latitude == null) {
                    latitude = BigDecimal.valueOf(loc.getLatitude()).setScale(7, RoundingMode.HALF_UP);
                    longitude = BigDecimal.valueOf(loc.getLongitude()).setScale(7, RoundingMode.HALF_UP);
                }
            }
            // 海拔
            if (dir.containsTag(GpsDirectory.TAG_ALTITUDE)) {
                Rational r = dir.getRational(GpsDirectory.TAG_ALTITUDE);
                if (r != null && altitude == null) {
                    altitude = BigDecimal.valueOf(r.doubleValue()).setScale(3, RoundingMode.HALF_UP);
                }
            }
        }

        return new BigDecimal[]{latitude, longitude, altitude};
    }

    /**
     * 提取GPS并返回可读字符串(调试用)
     */
    public static String extractGpsAsString(File imageFile) {
        BigDecimal[] gps = extractGps(imageFile);
        if (gps[0] == null && gps[1] == null) {
            return "无GPS";
        }
        return String.format("lat=%s, lon=%s, alt=%s",
                gps[0] != null ? gps[0].toPlainString() : "N/A",
                gps[1] != null ? gps[1].toPlainString() : "N/A",
                gps[2] != null ? gps[2].toPlainString() : "N/A");
    }
}

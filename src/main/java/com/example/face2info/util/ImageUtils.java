package com.example.face2info.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
/**
 * 图片处理工具。
 * 负责上传文件校验和 Base64 编码。
 */
public class ImageUtils {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final List<String> SUPPORTED_TYPES = List.of("image/jpeg", "image/png");
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");

    public void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("图片不能为空。");
        }
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 10MB。");
        }
        String contentType = image.getContentType();
        String originalFilename = image.getOriginalFilename();
        boolean validContentType = SUPPORTED_TYPES.contains(contentType);
        boolean validExtension = StringUtils.hasText(originalFilename)
                && SUPPORTED_EXTENSIONS.stream().anyMatch(ext ->
                originalFilename.toLowerCase(Locale.ROOT).endsWith(ext));
        if (!validContentType && !validExtension) {
            throw new IllegalArgumentException("仅支持 jpg 或 png 格式图片。");
        }
    }

    public String toBase64(MultipartFile image) {
        try {
            return Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException ex) {
            throw new IllegalArgumentException("图片读取失败。", ex);
        }
    }
}

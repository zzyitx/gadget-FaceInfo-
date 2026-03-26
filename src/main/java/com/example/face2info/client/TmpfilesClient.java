package com.example.face2info.client;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 临时文件上传客户端抽象。
 * 用于把本地文件或上传图片转换为可公开访问的临时 URL。
 */
public interface TmpfilesClient {

    /**
     * 上传本地文件并返回预览地址。
     */
    String uploadImage(File image);

    /**
     * 上传 MultipartFile 并返回预览地址。
     */
    String uploadImage(MultipartFile image);
}

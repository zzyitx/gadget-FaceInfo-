package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FaceCheck 上传接口响应")
public class FaceCheckUploadResponse {

    @Schema(description = "上传成功后返回的搜索任务标识")
    private String idSearch;

    @Schema(description = "接口响应消息")
    private String message;

    public String getIdSearch() {
        return idSearch;
    }

    public FaceCheckUploadResponse setIdSearch(String idSearch) {
        this.idSearch = idSearch;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public FaceCheckUploadResponse setMessage(String message) {
        this.message = message;
        return this;
    }
}

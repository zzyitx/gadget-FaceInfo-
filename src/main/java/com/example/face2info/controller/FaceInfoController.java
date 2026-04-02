package com.example.face2info.controller;

import com.example.face2info.entity.response.ErrorResponse;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.service.Face2InfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "Face2Info", description = "人脸图片识别与人物公开信息聚合接口")
@RestController
@RequestMapping("/api")
public class FaceInfoController {

    private final Face2InfoService face2InfoService;

    public FaceInfoController(Face2InfoService face2InfoService) {
        this.face2InfoService = face2InfoService;
    }

    @Operation(
            summary = "上传人脸图片并聚合人物公开信息",
            description = "接收前端上传的人脸图片，执行候选人物识别、公开资料抓取与结果聚合，返回统一结构的响应数据。",
            responses = {
                    @ApiResponse(responseCode = "200", description = "识别并聚合成功",
                            content = @Content(schema = @Schema(implementation = FaceInfoResponse.class))),
                    @ApiResponse(responseCode = "400", description = "参数错误或识别失败",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "外部 API 暂时不可用",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "服务内部异常",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping(value = "/face2info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceInfoResponse face2info(
            @Parameter(
                    description = "图片文件，仅支持常见图片格式的人脸照片",
                    required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("image") MultipartFile image) {
        log.info("控制器收到图片聚合请求 fileName={} size={} contentType={}",
                image.getOriginalFilename(), image.getSize(), image.getContentType());
        return face2InfoService.process(image);
    }
}

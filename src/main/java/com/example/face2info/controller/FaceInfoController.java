package com.example.face2info.controller;

import com.example.face2info.entity.response.ErrorResponse;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.service.Face2InfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Face2Info 对外 HTTP 接口。
 */
@Tag(name = "Face2Info")
@RestController
@RequestMapping("/api")
public class FaceInfoController {

    @Autowired
    private Face2InfoService face2InfoService;

    /**
     * 接收前端上传的人脸图片，并触发完整聚合流程。
     *
     * @param image 用户上传的图片
     * @return 聚合响应
     */
    @Operation(
            summary = "上传人脸图片并聚合人物公开信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "识别成功",
                            content = @Content(schema = @Schema(implementation = FaceInfoResponse.class))),
                    @ApiResponse(responseCode = "400", description = "参数错误或识别失败",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "外部 API 不可用",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping(value = "/face2info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceInfoResponse face2info(@RequestPart("image") MultipartFile image) {
        return face2InfoService.process(image);
    }
}

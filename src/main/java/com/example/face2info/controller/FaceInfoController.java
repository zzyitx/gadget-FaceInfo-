package com.example.face2info.controller;

import com.example.face2info.entity.response.ErrorResponse;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.service.Face2InfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Face2Info")
@RestController
@RequestMapping("/api")
/**
 * Face2Info 对外 HTTP 接口。
 */
public class FaceInfoController {

    private final Face2InfoService face2InfoService;

    public FaceInfoController(Face2InfoService face2InfoService) {
        this.face2InfoService = face2InfoService;
    }

    @Operation(
            summary = "上传人脸图片并聚合人物信息",
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

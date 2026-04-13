package com.example.face2info.controller;

import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.entity.response.DetectedFaceResponse;
import com.example.face2info.entity.response.ErrorResponse;
import com.example.face2info.entity.response.FaceDetectionResponse;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.FaceSelectionRequest;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Slf4j
@Tag(name = "Face2Info", description = "人脸图片识别与人物公开信息聚合接口")
@RestController
@RequestMapping("/api")
public class FaceInfoController {

    private final Face2InfoService face2InfoService;
    private final FaceDetectionService faceDetectionService;

    public FaceInfoController(Face2InfoService face2InfoService, FaceDetectionService faceDetectionService) {
        this.face2InfoService = face2InfoService;
        this.faceDetectionService = faceDetectionService;
    }

    @Operation(
            summary = "根据上传图片聚合人物公开信息",
            description = "接收上传的人脸图片，系统内部会先上传到图床获取 URL，再执行候选人物识别、公开资料抓取与结果聚合。",
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
    public FaceInfoResponse face2info(@RequestParam("image") MultipartFile image) {
        // 仅做入口接收与日志记录，业务编排统一下沉到 service 层。
        log.info("控制器收到聚合请求 fileName={} size={}", image.getOriginalFilename(), image.getSize());
        return face2InfoService.process(image);
    }

    @Operation(
            summary = "根据上传图片检测图片中的多张人脸",
            description = "接收上传图片，系统内部会先上传到图床获取 URL，再返回检测会话、预览图以及人脸边界框信息。"
    )
    @PostMapping(value = "/face2info/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaceDetectionResponse detect(@RequestParam("image") MultipartFile image) {
        // 检测接口与聚合接口共享上传参数约定，统一使用 image 字段。
        log.info("控制器收到人脸检测请求 fileName={} size={}", image.getOriginalFilename(), image.getSize());
        return mapDetectionSession(faceDetectionService.detect(image));
    }

    @Operation(
            summary = "基于检测结果处理选中的人脸",
            description = "接收 detection_id 和 face_id，从检测会话中取出目标裁剪图并进入既有人脸聚合流程。"
    )
    @PostMapping(value = "/face2info/process-selected", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FaceInfoResponse processSelected(@RequestBody FaceSelectionRequest request) {
        log.info("控制器收到选脸处理请求 detectionId={} faceId={}",
                request.getDetectionId(), request.getFaceId());
        return face2InfoService.processSelectedFace(request.getDetectionId(), request.getFaceId());
    }

    private FaceDetectionResponse mapDetectionSession(DetectionSession session) {
        FaceDetectionResponse response = new FaceDetectionResponse()
                .setDetectionId(session.getDetectionId())
                .setPreviewImage(session.getPreviewImage())
                .setEnhancedImageUrl(session.getEnhancedImageUrl());
        if (session.getFaces() != null) {
            for (DetectedFace detectedFace : session.getFaces()) {
                response.getFaces().add(mapDetectedFace(detectedFace));
            }
        }
        return response;
    }

    private DetectedFaceResponse mapDetectedFace(DetectedFace detectedFace) {
        SelectedFaceCrop crop = detectedFace.getSelectedFaceCrop();
        return new DetectedFaceResponse()
                .setFaceId(detectedFace.getFaceId())
                .setConfidence(detectedFace.getConfidence())
                .setBbox(detectedFace.getFaceBoundingBox())
                .setCropPreview(toDataUrl(crop));
    }

    private String toDataUrl(SelectedFaceCrop crop) {
        if (crop == null || crop.getBytes() == null || crop.getBytes().length == 0) {
            return null;
        }
        String contentType = crop.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/jpeg";
        }
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(crop.getBytes());
    }
}

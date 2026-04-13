package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.entity.response.DetectedFaceResponse;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.FaceSelectionPayload;
import com.example.face2info.entity.response.PersonBasicInfoResponse;
import com.example.face2info.entity.response.PersonInfo;
import com.example.face2info.service.Face2InfoService;
import com.example.face2info.service.FaceDetectionService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.ImageResultCacheService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import com.example.face2info.util.InMemoryMultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class Face2InfoServiceImpl implements Face2InfoService {

    private static final String NO_FACE_ERROR = "未检测到人脸，请更换更清晰的人脸图片。";
    private static final String MISSING_CROP_ERROR = "所选人脸裁剪图缺失或为空。";
    private static final String BLANK_DETECTION_ID_ERROR = "detection_id 不能为空。";
    private static final String BLANK_FACE_ID_ERROR = "face_id 不能为空。";
    private static final String PERSON_RESOLUTION_ERROR = "暂时无法解析人物信息。";

    private final ImageUtils imageUtils;
    private final FaceRecognitionService faceRecognitionService;
    private final InformationAggregationService informationAggregationService;
    private final FaceDetectionService faceDetectionService;
    private final ImageResultCacheService imageResultCacheService;

    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService,
                                FaceDetectionService faceDetectionService,
                                ImageResultCacheService imageResultCacheService) {
        this.imageUtils = imageUtils;
        this.faceRecognitionService = faceRecognitionService;
        this.informationAggregationService = informationAggregationService;
        this.faceDetectionService = faceDetectionService;
        this.imageResultCacheService = imageResultCacheService;
    }

    @Override
    public FaceInfoResponse process(MultipartFile image) {
        log.info("总流程开始 fileName={} size={}", image.getOriginalFilename(), image.getSize());
        imageUtils.validateImage(image);

        DetectionSession session = faceDetectionService.detect(image);
        int faceCount = session == null || session.getFaces() == null ? 0 : session.getFaces().size();
        if (faceCount == 0) {
            return buildFailedResponse(NO_FACE_ERROR);
        }
        if (faceCount > 1) {
            return buildSelectionRequiredResponse(session);
        }

        SelectedFaceCrop crop = session.getFaces().get(0) == null ? null : session.getFaces().get(0).getSelectedFaceCrop();
        return processSelectedCrop(crop);
    }

    @Override
    public FaceInfoResponse processSelectedFace(String detectionId, String faceId) {
        if (!StringUtils.hasText(detectionId)) {
            return buildFailedResponse(BLANK_DETECTION_ID_ERROR);
        }
        if (!StringUtils.hasText(faceId)) {
            return buildFailedResponse(BLANK_FACE_ID_ERROR);
        }

        SelectedFaceCrop crop = faceDetectionService.getSelectedFaceCrop(detectionId, faceId);
        return processSelectedCrop(crop);
    }

    private FaceInfoResponse processSelectedCrop(SelectedFaceCrop crop) {
        if (!hasCropContent(crop)) {
            return buildFailedResponse(MISSING_CROP_ERROR);
        }
        return processRecognizedImage(toMultipartFile(crop));
    }

    private boolean hasCropContent(SelectedFaceCrop crop) {
        return crop != null && crop.getBytes() != null && crop.getBytes().length > 0;
    }

    private MultipartFile toMultipartFile(SelectedFaceCrop crop) {
        return new InMemoryMultipartFile(crop.getFilename(), crop.getContentType(), crop.getBytes());
    }

    private FaceInfoResponse buildSelectionRequiredResponse(DetectionSession session) {
        FaceSelectionPayload selection = new FaceSelectionPayload()
                .setDetectionId(session.getDetectionId())
                .setPreviewImage(session.getPreviewImage())
                .setEnhancedImageUrl(session.getEnhancedImageUrl());

        if (session.getFaces() != null) {
            for (DetectedFace detectedFace : session.getFaces()) {
                selection.getFaces().add(mapDetectedFace(detectedFace));
            }
        }

        return new FaceInfoResponse()
                .setStatus("selection_required")
                .setSelection(selection);
    }

    private DetectedFaceResponse mapDetectedFace(DetectedFace detectedFace) {
        SelectedFaceCrop crop = detectedFace == null ? null : detectedFace.getSelectedFaceCrop();
        return new DetectedFaceResponse()
                .setFaceId(detectedFace == null ? null : detectedFace.getFaceId())
                .setConfidence(detectedFace == null ? 0.0 : detectedFace.getConfidence())
                .setBbox(detectedFace == null ? null : detectedFace.getFaceBoundingBox())
                .setCropPreview(toDataUrl(crop));
    }

    private FaceInfoResponse buildFailedResponse(String error) {
        return new FaceInfoResponse()
                .setStatus("failed")
                .setError(normalizeUserMessage(error));
    }

    private FaceInfoResponse processRecognizedImage(MultipartFile image) {
        log.info("总流程开始 fileName={} size={}", image.getOriginalFilename(), image.getSize());
        imageUtils.validateImage(image);

        // 最终响应缓存命中时可直接返回，减少识别与聚合开销。
        FaceInfoResponse cachedResponse = imageResultCacheService.getFaceInfoResponse(image);
        if (cachedResponse != null) {
            log.info("最终响应命中缓存 fileName={} status={}", image.getOriginalFilename(), cachedResponse.getStatus());
            return cachedResponse;
        }

        // 识别阶段负责生成候选人物、图片匹配与网页证据。
        RecognitionEvidence evidence = faceRecognitionService.recognize(image);
        // 聚合阶段将识别证据转换为统一人物画像与公开信息集合。
        AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);

        List<String> combinedErrors = normalizeMessages(safeCopy(aggregationResult == null ? null : aggregationResult.getErrors()));
        List<String> warnings = normalizeMessages(safeCopy(aggregationResult == null ? null : aggregationResult.getWarnings()));

        if (aggregationResult == null || aggregationResult.getPerson() == null || !StringUtils.hasText(aggregationResult.getPerson().getName())) {
            List<String> errors = normalizeMessages(safeCopy(evidence == null ? null : evidence.getErrors()));
            errors.addAll(combinedErrors);
            FaceInfoResponse response = new FaceInfoResponse()
                    .setPerson(null)
                    .setNews(aggregationResult == null ? null : aggregationResult.getNews())
                    .setImageMatches(evidence == null ? null : evidence.getImageMatches())
                    .setWarnings(warnings)
                    .setStatus("failed")
                    .setError(errors.isEmpty() ? PERSON_RESOLUTION_ERROR : String.join("; ", errors));
            // 失败态也缓存，避免同一输入反复触发高成本外部调用。
            imageResultCacheService.cacheFaceInfoResponse(image, response);
            return response;
        }

        // 仅对外暴露稳定响应模型字段，不透出第三方原始结构。
        PersonInfo person = new PersonInfo()
                .setName(aggregationResult.getPerson().getName())
                .setDescription(aggregationResult.getPerson().getDescription())
                .setImageUrl(aggregationResult.getPerson().getImageUrl())
                .setSummary(aggregationResult.getPerson().getSummary())
                .setTags(aggregationResult.getPerson().getTags())
                .setEvidenceUrls(aggregationResult.getPerson().getEvidenceUrls())
                .setWikipedia(aggregationResult.getPerson().getWikipedia())
                .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
                .setBasicInfo(toResponseBasicInfo(aggregationResult.getPerson().getBasicInfo()))
                .setSocialAccounts(aggregationResult.getSocialAccounts());

        String status = (!combinedErrors.isEmpty() || !warnings.isEmpty()) ? "partial" : "success";
        FaceInfoResponse response = new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setWarnings(warnings)
                .setImageMatches(evidence == null ? null : evidence.getImageMatches())
                .setStatus(status)
                .setError(combinedErrors.isEmpty() ? null : String.join("; ", combinedErrors));
        // 成功/部分成功结果统一缓存，缩短后续重复请求时延。
        imageResultCacheService.cacheFaceInfoResponse(image, response);
        return response;
    }

    private List<String> safeCopy(List<String> values) {
        return new ArrayList<>(values == null ? List.of() : values);
    }

    private List<String> normalizeMessages(List<String> messages) {
        List<String> normalized = new ArrayList<>(messages.size());
        for (String message : messages) {
            normalized.add(normalizeUserMessage(message));
        }
        return normalized;
    }

    private String normalizeUserMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return message;
        }
        return message
                .replace("Selected face crop is missing or empty.", MISSING_CROP_ERROR)
                .replace("detection_id must not be blank.", BLANK_DETECTION_ID_ERROR)
                .replace("face_id must not be blank.", BLANK_FACE_ID_ERROR)
                .replace("Unable to resolve person information.", PERSON_RESOLUTION_ERROR)
                .replace("Unable to resolve person name from evidence", "无法解析人物名称")
                .replace("news fetch failed: timeout", "新闻抓取失败：请求超时")
                .replace("bing_images: timeout", "Bing 图片搜索超时");
    }

    private String toDataUrl(SelectedFaceCrop crop) {
        if (!hasCropContent(crop)) {
            return null;
        }
        String contentType = crop.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/jpeg";
        }
        return "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(crop.getBytes());
    }

    private PersonBasicInfoResponse toResponseBasicInfo(PersonBasicInfo basicInfo) {
        if (basicInfo == null) {
            return new PersonBasicInfoResponse();
        }
        return new PersonBasicInfoResponse()
                .setBirthDate(basicInfo.getBirthDate())
                .setEducation(basicInfo.getEducation())
                .setOccupations(basicInfo.getOccupations())
                .setBiographies(basicInfo.getBiographies());
    }
}

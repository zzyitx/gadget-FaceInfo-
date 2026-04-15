package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.PersonBasicInfo;
import com.example.face2info.entity.internal.PreparedImageResult;
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
import com.example.face2info.service.EnhancedImagePreparationService;
import com.example.face2info.service.ImageResultCacheService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import com.example.face2info.util.InMemoryMultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final EnhancedImagePreparationService enhancedImagePreparationService;
    private final boolean usePreparedPipeline;

    @Autowired
    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService,
                                FaceDetectionService faceDetectionService,
                                ImageResultCacheService imageResultCacheService,
                                EnhancedImagePreparationService enhancedImagePreparationService) {
        this(
                imageUtils,
                faceRecognitionService,
                informationAggregationService,
                faceDetectionService,
                imageResultCacheService,
                enhancedImagePreparationService,
                true
        );
    }

    private Face2InfoServiceImpl(ImageUtils imageUtils,
                                 FaceRecognitionService faceRecognitionService,
                                 InformationAggregationService informationAggregationService,
                                 FaceDetectionService faceDetectionService,
                                 ImageResultCacheService imageResultCacheService,
                                 EnhancedImagePreparationService enhancedImagePreparationService,
                                 boolean usePreparedPipeline) {
        this.imageUtils = imageUtils;
        this.faceRecognitionService = faceRecognitionService;
        this.informationAggregationService = informationAggregationService;
        this.faceDetectionService = faceDetectionService;
        this.imageResultCacheService = imageResultCacheService;
        this.enhancedImagePreparationService = enhancedImagePreparationService;
        this.usePreparedPipeline = usePreparedPipeline;
    }

    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService,
                                FaceDetectionService faceDetectionService,
                                ImageResultCacheService imageResultCacheService) {
        this(
                imageUtils,
                faceRecognitionService,
                informationAggregationService,
                faceDetectionService,
                imageResultCacheService,
                originalImage -> new PreparedImageResult()
                        .setOriginalImage(originalImage)
                        .setWorkingImage(originalImage),
                false
        );
    }

    @Override
    public FaceInfoResponse process(MultipartFile image) {
        // 主入口：先做人脸检测，再按单脸/多脸分流后续流程。
        return process(image, null);
    }

    @Override
    public FaceInfoResponse processSelectedFace(String detectionId, String faceId) {
        if (!StringUtils.hasText(detectionId)) {
            return buildFailedResponse(BLANK_DETECTION_ID_ERROR);
        }
        if (!StringUtils.hasText(faceId)) {
            return buildFailedResponse(BLANK_FACE_ID_ERROR);
        }

        // 二次请求只依赖 detection session，不要求前端重复上传原图。
        SelectedFaceCrop crop = faceDetectionService.getSelectedFaceCrop(detectionId, faceId);
        return processSelectedCrop(crop);
    }

    private FaceInfoResponse process(MultipartFile image, String imageUrl) {
        log.info("总流程开始 fileName={} size={} sourceUrl={}", image.getOriginalFilename(), image.getSize(), imageUrl);
        imageUtils.validateImage(image);

        if (!usePreparedPipeline) {
            return processLegacy(image, imageUrl);
        }

        // 新主流程先生成 preparedImageResult，保证整条链路不再混用原图和高清图。
        PreparedImageResult preparedImageResult = enhancedImagePreparationService.prepare(image);
        DetectionSession session = faceDetectionService.detect(preparedImageResult);
        List<String> preparationWarnings = collectPreparationWarnings(preparedImageResult, session);
        int faceCount = session == null || session.getFaces() == null ? 0 : session.getFaces().size();
        if (faceCount == 0) {
            return buildFailedResponse(NO_FACE_ERROR, preparationWarnings);
        }
        if (faceCount > 1) {
            // 多脸不是失败场景，返回 selection_required 让前端显式选定目标人脸。
            return buildSelectionRequiredResponse(session, preparationWarnings);
        }

        // 单脸场景直接沿用最终工作图继续识别；不依赖裁剪图可减少空裁剪导致的失败。
        String uploadedImageUrl = StringUtils.hasText(preparedImageResult.getUploadedImageUrl())
                ? preparedImageResult.getUploadedImageUrl()
                : imageUrl;
        return processRecognizedImage(preparedImageResult.getWorkingImage(), uploadedImageUrl, preparationWarnings);
    }

    private FaceInfoResponse processLegacy(MultipartFile image, String imageUrl) {
        DetectionSession session = faceDetectionService.detect(image);
        int faceCount = session == null || session.getFaces() == null ? 0 : session.getFaces().size();
        if (faceCount == 0) {
            return buildFailedResponse(NO_FACE_ERROR);
        }
        if (faceCount > 1) {
            return buildSelectionRequiredResponse(session, List.of());
        }
        return processRecognizedImage(image, imageUrl, List.of());
    }

    private FaceInfoResponse processSelectedCrop(SelectedFaceCrop crop) {
        return processSelectedCrop(crop, null);
    }

    private FaceInfoResponse processSelectedCrop(SelectedFaceCrop crop, String imageUrl) {
        if (!hasCropContent(crop)) {
            return buildFailedResponse(MISSING_CROP_ERROR);
        }
        // 选脸接口使用裁剪图继续识别，保持与用户选择的人脸一致。
        return processRecognizedImage(toMultipartFile(crop), imageUrl, List.of());
    }

    private FaceInfoResponse processRecognizedImage(MultipartFile image, String imageUrl, List<String> preparationWarnings) {
        log.info("聚合流程开始 fileName={} size={} sourceUrl={}", image.getOriginalFilename(), image.getSize(), imageUrl);
        imageUtils.validateImage(image);

        // 命中最终响应缓存时直接返回，避免重复调用识别与聚合链路。
        FaceInfoResponse cachedResponse = imageResultCacheService.getFaceInfoResponse(image);
        if (cachedResponse != null) {
            log.info("最终响应命中缓存 fileName={} status={}", image.getOriginalFilename(), cachedResponse.getStatus());
            return cachedResponse;
        }

        RecognitionEvidence evidence = StringUtils.hasText(imageUrl)
                ? faceRecognitionService.recognize(image, imageUrl)
                : faceRecognitionService.recognize(image);
        AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);

        List<String> combinedErrors = normalizeMessages(safeCopy(aggregationResult == null ? null : aggregationResult.getErrors()));
        List<String> warnings = normalizeMessages(safeCopy(aggregationResult == null ? null : aggregationResult.getWarnings()));
        // 把高清化降级 warning 合并到最终响应，保证前端能感知“已回退原图继续处理”。
        warnings.addAll(normalizeMessages(safeCopy(preparationWarnings)));

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
            imageResultCacheService.cacheFaceInfoResponse(image, response);
            return response;
        }

        PersonInfo person = new PersonInfo()
                .setName(aggregationResult.getPerson().getName())
                .setImageUrl(aggregationResult.getPerson().getImageUrl())
                .setSummary(aggregationResult.getPerson().getSummary())
                .setEducationSummary(aggregationResult.getPerson().getEducationSummary())
                .setFamilyBackgroundSummary(aggregationResult.getPerson().getFamilyBackgroundSummary())
                .setCareerSummary(aggregationResult.getPerson().getCareerSummary())
                .setChinaRelatedStatementsSummary(aggregationResult.getPerson().getChinaRelatedStatementsSummary())
                .setPoliticalTendencySummary(aggregationResult.getPerson().getPoliticalTendencySummary())
                .setContactInformationSummary(aggregationResult.getPerson().getContactInformationSummary())
                .setFamilyMemberSituationSummary(aggregationResult.getPerson().getFamilyMemberSituationSummary())
                .setMisconductSummary(aggregationResult.getPerson().getMisconductSummary())
                .setTags(aggregationResult.getPerson().getTags())
                .setEvidenceUrls(aggregationResult.getPerson().getEvidenceUrls())
                .setWikipedia(aggregationResult.getPerson().getWikipedia())
                .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
                .setBasicInfo(toResponseBasicInfo(aggregationResult.getPerson().getBasicInfo()))
                .setSocialAccounts(aggregationResult.getSocialAccounts());

        // 只要有 errors/warnings 就返回 partial，向调用方明确“结果可用但不完整”。
        String status = (!combinedErrors.isEmpty() || !warnings.isEmpty()) ? "partial" : "success";
        FaceInfoResponse response = new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setWarnings(warnings)
                .setImageMatches(evidence == null ? null : evidence.getImageMatches())
                .setStatus(status)
                .setError(combinedErrors.isEmpty() ? null : String.join("; ", combinedErrors));
        imageResultCacheService.cacheFaceInfoResponse(image, response);
        return response;
    }

    private boolean hasCropContent(SelectedFaceCrop crop) {
        return crop != null && crop.getBytes() != null && crop.getBytes().length > 0;
    }

    private MultipartFile toMultipartFile(SelectedFaceCrop crop) {
        return new InMemoryMultipartFile(crop.getFilename(), crop.getContentType(), crop.getBytes());
    }

    private FaceInfoResponse buildSelectionRequiredResponse(DetectionSession session, List<String> preparationWarnings) {
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
                .setWarnings(normalizeMessages(safeCopy(preparationWarnings)))
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

    private FaceInfoResponse buildFailedResponse(String error, List<String> warnings) {
        return new FaceInfoResponse()
                .setStatus("failed")
                .setWarnings(normalizeMessages(safeCopy(warnings)))
                .setError(normalizeUserMessage(error));
    }

    private List<String> collectPreparationWarnings(PreparedImageResult preparedImageResult, DetectionSession session) {
        List<String> warnings = new ArrayList<>();
        if (preparedImageResult != null && StringUtils.hasText(preparedImageResult.getWarning())) {
            warnings.add(preparedImageResult.getWarning());
        }
        if (session != null && StringUtils.hasText(session.getEnhancementWarning())
                && warnings.stream().noneMatch(session.getEnhancementWarning()::equals)) {
            warnings.add(session.getEnhancementWarning());
        }
        return warnings;
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

package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.ParagraphSource;
import com.example.face2info.entity.internal.ParagraphSummaryItem;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.FaceSelectionPayload;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.service.FaceDetectionService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.EnhancedImagePreparationService;
import com.example.face2info.service.ImageResultCacheService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Face2InfoServiceImplTest {

    @Test
    void shouldAppendEnhancementWarningWhenPreparationFallsBack() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceDetectionService faceDetectionService = mock(FaceDetectionService.class);
        ImageResultCacheService imageResultCacheService = mock(ImageResultCacheService.class);
        EnhancedImagePreparationService enhancedImagePreparationService = mock(EnhancedImagePreparationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PreparedImageResult prepared = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview")
                .setEnhancementApplied(false)
                .setWarning("图片高清化失败，已自动回退原图继续处理。");
        RecognitionEvidence evidence = new RecognitionEvidence();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setUploadedImageUrl("https://tempfile.org/original/preview")
                .setEnhancementWarning("图片高清化失败，已自动回退原图继续处理。")
                .setFaces(List.of(buildDetectedFace("face-1", "face-1.jpg")));
        doNothing().when(imageUtils).validateImage(image);
        when(enhancedImagePreparationService.prepare(image)).thenReturn(prepared);
        when(faceDetectionService.detect(prepared)).thenReturn(session);
        when(recognitionService.recognize(image, "https://tempfile.org/original/preview")).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Lei Jun")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils,
                recognitionService,
                aggregationService,
                faceDetectionService,
                imageResultCacheService,
                enhancedImagePreparationService
        );

        FaceInfoResponse response = service.process(image);

        assertThat(response.getWarnings()).contains("图片高清化失败，已自动回退原图继续处理。");
    }

    @Test
    void shouldReturnSelectionRequiredWhenMultipleFacesDetected() {
        ServiceFixture fixture = createFixture();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setPreviewImage("data:image/png;base64,preview")
                .setFaces(List.of(
                        buildDetectedFace("face-1", "face-1.jpg"),
                        buildDetectedFace("face-2", "face-2.jpg")));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("selection_required");
        assertThat(response.getSelection()).isNotNull();
        assertThat(response.getSelection().getDetectionId()).isEqualTo("det-1");
        assertThat(response.getSelection().getPreviewImage()).isEqualTo("data:image/png;base64,preview");
        assertThat(response.getSelection().getFaces()).hasSize(2);
        assertThat(response.getSelection().getFaces().get(0).getFaceId()).isEqualTo("face-1");
        verify(fixture.faceRecognitionService, never()).recognize(any());
        verify(fixture.informationAggregationService, never()).aggregate(any());
    }

    @Test
    void shouldProcessRecognizedImageWhenExactlyOneFaceDetected() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence().setImageMatches(List.of(
                new ImageMatch()
                        .setPosition(1)
                        .setTitle("Lei Jun official profile")
                        .setLink("https://example.com/official")
                        .setSource("Wikipedia")
                        .setThumbnailUrl("https://thumb.example.com/official.jpg")
                        .setSimilarityScore(96.4)
        )).setArticleImageMatches(List.of(
                new ImageMatch()
                        .setPosition(1)
                        .setTitle("Lei Jun official profile")
                        .setLink("https://example.com/official")
                        .setSource("Wikipedia")
                        .setThumbnailUrl("https://thumb.example.com/official.jpg")
                        .setSimilarityScore(96.4),
                new ImageMatch()
                        .setPosition(2)
                        .setTitle("Lei Jun article copy")
                        .setLink("https://example.com/article")
                        .setSource("Example")
                        .setThumbnailUrl("https://thumb.example.com/article.jpg")
                        .setSimilarityScore(74.2)
        ));
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(buildDetectedFace("face-1", "face-1.jpg")));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Lei Jun").setSummary("Lei Jun is a technology entrepreneur.")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getPerson().getSummary()).isEqualTo("Lei Jun is a technology entrepreneur.");
        assertThat(response.getImageMatches()).hasSize(1);
        assertThat(response.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/official.jpg");
        assertThat(response.getImageMatches().get(0).getSimilarityScore()).isEqualTo(96.4);
        assertThat(response.getArticleImageMatches()).hasSize(2);
        assertThat(response.getArticleImageMatches().get(1).getLink()).isEqualTo("https://example.com/article");
        assertHasFlowTiming(response);
        verify(fixture.faceDetectionService).detect(fixture.image);
        verify(fixture.faceRecognitionService).recognize(argThat(file ->
                "face.jpg".equals(file.getOriginalFilename()) && file.getSize() == 3));
    }

    @Test
    void shouldFailWhenNoFaceDetected() {
        ServiceFixture fixture = createFixture();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of());

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getError()).contains("未检测到人脸");
        verify(fixture.faceRecognitionService, never()).recognize(any());
        verify(fixture.informationAggregationService, never()).aggregate(any());
    }

    @Test
    void shouldContinueWithOriginalImageWhenSingleFaceCropIsMissing() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(null)));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Lei Jun")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("success");
        verify(fixture.faceRecognitionService).recognize(argThat(file ->
                "face.jpg".equals(file.getOriginalFilename()) && file.getSize() == 3));
    }

    @Test
    void shouldContinueWithOriginalImageWhenSingleFaceCropBytesAreEmpty() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[0]))));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Lei Jun")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("success");
        verify(fixture.faceRecognitionService).recognize(argThat(file ->
                "face.jpg".equals(file.getOriginalFilename()) && file.getSize() == 3));
    }

    @Test
    void shouldReturnFailedWhenSelectedFaceCropIsMissingForProcessSelectedFace() {
        ServiceFixture fixture = createFixture();
        when(fixture.faceDetectionService.getSelectedFaceCrop("det-1", "face-1")).thenReturn(null);

        FaceInfoResponse response = fixture.service.processSelectedFace("det-1", "face-1");

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getError()).contains("人脸裁剪图");
        verify(fixture.faceRecognitionService, never()).recognize(any());
        verify(fixture.informationAggregationService, never()).aggregate(any());
    }

    @Test
    void shouldReturnFailedWhenProcessSelectedFaceInputsAreBlank() {
        ServiceFixture fixture = createFixture();

        FaceInfoResponse response = fixture.service.processSelectedFace("  ", "face-1");

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getError()).contains("detection_id 不能为空");
        verify(fixture.faceDetectionService, never()).getSelectedFaceCrop(any(), any());
    }

    @Test
    void shouldReturnFailedWhenFaceIdIsBlankInProcessSelectedFace() {
        ServiceFixture fixture = createFixture();

        FaceInfoResponse response = fixture.service.processSelectedFace("det-1", " ");

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getError()).contains("face_id 不能为空");
        verify(fixture.faceDetectionService, never()).getSelectedFaceCrop(any(), any());
    }

    @Test
    void shouldMapSummaryTagsAndWarningsToResponse() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate()
                        .setName("Jay Chou")
                        .setSummary("Jay Chou is a major Mandopop artist.")
                        .setTags(List.of("Singer", "Producer"))
                        .setTotalArticlesRead(7)
                        .setFinalArticlesUsed(3))
                .setWarnings(List.of("Summary provider unavailable")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getPerson().getSummary()).isEqualTo("Jay Chou is a major Mandopop artist.");
        assertThat(response.getPerson().getTags()).containsExactly("Singer", "Producer");
        assertThat(response.getPerson().getTotalArticlesRead()).isEqualTo(7);
        assertThat(response.getPerson().getFinalArticlesUsed()).isEqualTo(3);
        assertThat(response.getWarnings()).containsExactly("Summary provider unavailable");
    }

    @Test
    void shouldMapBasicInfoIntoResponse() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate()
                        .setName("Jay Chou")
                        .setImageUrl("https://example.com/avatar.jpg")
                        .setSummary("Long summary")
                        .setWikipedia("https://example.com/wiki")
                        .setOfficialWebsite("https://example.com")
                        .setSummaryParagraphs(List.of(
                                new ParagraphSummaryItem()
                                        .setText("第一段主体信息。")
                                        .setSources(List.of(
                                                new ParagraphSource()
                                                        .setTitle("文章 A")
                                                        .setUrl("https://example.com/a")
                                                        .setSource("Example")
                                        ))
                        ))
                        .setEducationSummaryParagraphs(List.of(
                                new ParagraphSummaryItem()
                                        .setText("第一段教育经历。")
                                        .setSources(List.of(
                                                new ParagraphSource()
                                                        .setTitle("教育文章")
                                                        .setUrl("https://example.com/education")
                                        ))
                        ))
                        .setBasicInfo(new com.example.face2info.entity.internal.PersonBasicInfo()
                                .setBirthDate("1979-01-18")
                                .setEducation(List.of("Tamkang Senior High School"))
                                .setOccupations(List.of("Singer", "Producer"))
                                .setBiographies(List.of("Taiwanese Mandopop artist")))));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getPerson().getImageUrl()).isEqualTo("https://example.com/avatar.jpg");
        assertThat(response.getPerson().getBasicInfo().getBirthDate()).isEqualTo("1979-01-18");
        assertThat(response.getPerson().getBasicInfo().getEducation()).containsExactly("Tamkang Senior High School");
        assertThat(response.getPerson().getBasicInfo().getOccupations()).containsExactly("Singer", "Producer");
        assertThat(response.getPerson().getBasicInfo().getBiographies()).containsExactly("Taiwanese Mandopop artist");
        assertThat(response.getPerson().getSummaryParagraphs()).hasSize(1);
        assertThat(response.getPerson().getSummaryParagraphs().get(0).getText()).isEqualTo("第一段主体信息。[1]");
        assertThat(response.getPerson().getSummaryParagraphs().get(0).getSources()).hasSize(1);
        assertThat(response.getPerson().getSummaryParagraphs().get(0).getSources().get(0).getTitle()).isEqualTo("文章 A");
        assertThat(response.getPerson().getEducationSummaryParagraphs()).hasSize(1);
        assertThat(response.getPerson().getEducationSummaryParagraphs().get(0).getText()).isEqualTo("第一段教育经历。[2]");
        assertThat(response.getPerson().getEducationSummaryParagraphs().get(0).getSources().get(0).getUrl())
                .isEqualTo("https://example.com/education");
    }

    @Test
    void shouldAssignStableCitationIndexesAcrossParagraphsAndAppendInlineMarkers() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence().setArticleImageMatches(List.of(
                new ImageMatch().setPosition(2).setTitle("文章 B").setLink("https://example.com/b").setSource("Example B"),
                new ImageMatch().setPosition(1).setTitle("文章 A").setLink("https://example.com/a").setSource("Example A")
        ));
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate()
                        .setName("Jay Chou")
                        .setSummaryParagraphs(List.of(
                                new ParagraphSummaryItem()
                                        .setText("主体段落")
                                        .setSources(List.of(
                                                new ParagraphSource().setTitle("文章 A").setUrl("https://example.com/a").setSource("Example A"),
                                                new ParagraphSource().setTitle("文章 B").setUrl("https://example.com/b").setSource("Example B")
                                        ))
                        ))
                        .setEducationSummaryParagraphs(List.of(
                                new ParagraphSummaryItem()
                                        .setText("教育段落")
                                        .setSources(List.of(
                                                new ParagraphSource().setTitle("文章 B").setUrl("https://example.com/b").setSource("Example B")
                                        ))
                        ))));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getPerson().getSummaryParagraphs()).hasSize(1);
        assertThat(response.getPerson().getSummaryParagraphs().get(0).getText()).isEqualTo("主体段落[1][2]");
        assertThat(response.getPerson().getSummaryParagraphs().get(0).getSources())
                .extracting(source -> source.getIndex())
                .containsExactly(1, 2);
        assertThat(response.getPerson().getEducationSummaryParagraphs()).hasSize(1);
        assertThat(response.getPerson().getEducationSummaryParagraphs().get(0).getText()).isEqualTo("教育段落[2]");
        assertThat(response.getPerson().getEducationSummaryParagraphs().get(0).getSources())
                .extracting(source -> source.getIndex())
                .containsExactly(2);
    }

    @Test
    void shouldReturnPartialWhenAggregationContainsErrors() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setSummary("Jay Chou is a singer."))
                .setErrors(List.of("bing_images: timeout")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("partial");
        assertThat(response.getError()).contains("Bing 图片搜索超时");
    }

    @Test
    void shouldReturnFailedWhenPersonCannotBeResolved() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence().setErrors(List.of("bing_images: timeout"));
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate())
                .setErrors(List.of("Unable to resolve person name from evidence")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getPerson()).isNull();
        assertThat(response.getError()).contains("无法解析人物名称");
    }

    @Test
    void shouldKeepWarningsEmptyWhenAggregationDoesNotProvideWarnings() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void shouldReturnCachedFinalResponseBeforeRecognitionAndAggregation() {
        ServiceFixture fixture = createFixture();
        FaceInfoResponse cached = new FaceInfoResponse()
                .setStatus("success")
                .setStartedAt("2000-01-01T00:00:00Z")
                .setFinishedAt("2000-01-01T00:00:01Z")
                .setDurationMs(1000L);
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.imageResultCacheService.getFaceInfoResponse(any())).thenReturn(cached);

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response).isSameAs(cached);
        assertHasFlowTiming(response);
        assertThat(response.getStartedAt()).isNotEqualTo("2000-01-01T00:00:00Z");
        assertThat(response.getFinishedAt()).isNotEqualTo("2000-01-01T00:00:01Z");
        assertThat(response.getDurationMs()).isNotEqualTo(1000L);
        verify(fixture.faceRecognitionService, never()).recognize(any());
        verify(fixture.informationAggregationService, never()).aggregate(any());
    }

    @Test
    void shouldCacheFinalResponseAfterRecognitionAndAggregation() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.imageResultCacheService.getFaceInfoResponse(any())).thenReturn(null);
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        verify(fixture.imageResultCacheService).cacheFaceInfoResponse(any(), org.mockito.ArgumentMatchers.same(response));
    }

    @Test
    void shouldTreatNullAggregationErrorsAndWarningsAsEmptyLists() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou"))
                .setErrors(null)
                .setWarnings(null));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getError()).isNull();
    }

    @Test
    void shouldProcessSelectedFaceCropInsteadOfOriginalImage() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceDetectionService faceDetectionService = mock(FaceDetectionService.class);
        ImageResultCacheService imageResultCacheService = mock(ImageResultCacheService.class);
        SelectedFaceCrop crop = new SelectedFaceCrop()
                .setFilename("face-1.jpg")
                .setContentType("image/jpeg")
                .setBytes(new byte[]{9, 9, 9});

        when(faceDetectionService.getSelectedFaceCrop("det-1", "face-1")).thenReturn(crop);
        when(recognitionService.recognize(any())).thenReturn(new RecognitionEvidence());
        when(aggregationService.aggregate(any())).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceDetectionService, imageResultCacheService);

        FaceInfoResponse response = service.processSelectedFace("det-1", "face-1");

        assertThat(response.getStatus()).isEqualTo("success");
        verify(imageUtils).validateImage(argThat(file -> "face-1.jpg".equals(file.getOriginalFilename())
                && "image/jpeg".equals(file.getContentType())));
        verify(recognitionService).recognize(argThat(file -> "face-1.jpg".equals(file.getOriginalFilename())));
        verify(faceDetectionService).getSelectedFaceCrop("det-1", "face-1");
    }

    @Test
    void shouldExposeSelectionPayload() {
        FaceSelectionPayload selection = new FaceSelectionPayload().setDetectionId("det-1");
        FaceInfoResponse response = new FaceInfoResponse().setSelection(selection);

        assertThat(response.getSelection()).isNotNull();
        assertThat(response.getSelection().getDetectionId()).isEqualTo("det-1");
    }

    @Test
    void shouldKeepResponseListsStableWhenSetterReceivesNull() {
        FaceInfoResponse response = new FaceInfoResponse()
                .setImageMatches(null)
                .setWarnings(null);

        assertThat(response.getImageMatches()).isEmpty();
        assertThat(response.getWarnings()).isEmpty();
    }

    private void assertHasFlowTiming(FaceInfoResponse response) {
        assertThat(response.getStartedAt()).isNotBlank();
        assertThat(response.getFinishedAt()).isNotBlank();
        assertThat(response.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(Instant.parse(response.getFinishedAt()))
                .isAfterOrEqualTo(Instant.parse(response.getStartedAt()));
    }

    private ServiceFixture createFixture() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceDetectionService faceDetectionService = mock(FaceDetectionService.class);
        ImageResultCacheService imageResultCacheService = mock(ImageResultCacheService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        doNothing().when(imageUtils).validateImage(image);
        return new ServiceFixture(
                new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService, faceDetectionService, imageResultCacheService),
                image,
                imageUtils,
                recognitionService,
                aggregationService,
                faceDetectionService,
                imageResultCacheService);
    }

    private DetectionSession singleFaceSession() {
        return new DetectionSession()
                .setDetectionId("det-1")
                .setPreviewImage("data:image/png;base64,preview")
                .setFaces(List.of(buildDetectedFace("face-1", "face-1.jpg")));
    }

    private DetectedFace buildDetectedFace(String faceId, String filename) {
        return new DetectedFace()
                .setFaceId(faceId)
                .setConfidence(0.98)
                .setFaceBoundingBox(new FaceBoundingBox().setX(12).setY(24).setWidth(100).setHeight(120))
                .setSelectedFaceCrop(new SelectedFaceCrop()
                        .setFilename(filename)
                        .setContentType("image/jpeg")
                        .setBytes(new byte[]{9, 9, 9}));
    }

    private record ServiceFixture(
            Face2InfoServiceImpl service,
            MockMultipartFile image,
            ImageUtils imageUtils,
            FaceRecognitionService faceRecognitionService,
            InformationAggregationService informationAggregationService,
            FaceDetectionService faceDetectionService,
            ImageResultCacheService imageResultCacheService) {
    }
}

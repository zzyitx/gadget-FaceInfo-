package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.FaceSelectionPayload;
import com.example.face2info.entity.response.ImageMatch;
import com.example.face2info.service.FaceDetectionService;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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
        ));
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(buildDetectedFace("face-1", "face-1.jpg")));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Lei Jun").setDescription("Lei Jun is a technology entrepreneur.")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getImageMatches()).hasSize(1);
        assertThat(response.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/official.jpg");
        assertThat(response.getImageMatches().get(0).getSimilarityScore()).isEqualTo(96.4);
        verify(fixture.faceDetectionService).detect(fixture.image);
        verify(fixture.faceRecognitionService).recognize(argThat(file ->
                "face-1.jpg".equals(file.getOriginalFilename()) && file.getSize() == 3));
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
    void shouldFailWhenSingleFaceCropIsMissing() {
        ServiceFixture fixture = createFixture();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(null)));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getError()).contains("人脸裁剪图");
        verify(fixture.faceRecognitionService, never()).recognize(any());
        verify(fixture.informationAggregationService, never()).aggregate(any());
    }

    @Test
    void shouldFailWhenSelectedFaceCropHasEmptyBytes() {
        ServiceFixture fixture = createFixture();
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[0]))));

        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(session);

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getError()).contains("人脸裁剪图");
        verify(fixture.faceRecognitionService, never()).recognize(any());
        verify(fixture.informationAggregationService, never()).aggregate(any());
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
                        .setDescription("Mandopop singer")
                        .setSummary("Jay Chou is a major Mandopop artist.")
                        .setTags(List.of("Singer", "Producer")))
                .setWarnings(List.of("Summary provider unavailable")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getPerson().getSummary()).isEqualTo("Jay Chou is a major Mandopop artist.");
        assertThat(response.getPerson().getTags()).containsExactly("Singer", "Producer");
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
                        .setDescription("Short description")
                        .setSummary("Long summary")
                        .setWikipedia("https://example.com/wiki")
                        .setOfficialWebsite("https://example.com")
                        .setBasicInfo(new com.example.face2info.entity.internal.PersonBasicInfo()
                                .setBirthDate("1979-01-18")
                                .setEducation(List.of("Tamkang Senior High School"))
                                .setOccupations(List.of("Singer", "Producer"))
                                .setBiographies(List.of("Taiwanese Mandopop artist")))));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getPerson().getBasicInfo().getBirthDate()).isEqualTo("1979-01-18");
        assertThat(response.getPerson().getBasicInfo().getEducation()).containsExactly("Tamkang Senior High School");
        assertThat(response.getPerson().getBasicInfo().getOccupations()).containsExactly("Singer", "Producer");
        assertThat(response.getPerson().getBasicInfo().getBiographies()).containsExactly("Taiwanese Mandopop artist");
    }

    @Test
    void shouldReturnPartialWhenAggregationContainsErrors() {
        ServiceFixture fixture = createFixture();
        RecognitionEvidence evidence = new RecognitionEvidence();
        when(fixture.faceDetectionService.detect(fixture.image)).thenReturn(singleFaceSession());
        when(fixture.faceRecognitionService.recognize(any())).thenReturn(evidence);
        when(fixture.informationAggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer."))
                .setErrors(List.of("news fetch failed: timeout")));

        FaceInfoResponse response = fixture.service.process(fixture.image);

        assertThat(response.getStatus()).isEqualTo("partial");
        assertThat(response.getError()).contains("新闻抓取失败");
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
        SelectedFaceCrop crop = new SelectedFaceCrop()
                .setFilename("face-1.jpg")
                .setContentType("image/jpeg")
                .setBytes(new byte[]{9, 9, 9});

        when(faceDetectionService.getSelectedFaceCrop("det-1", "face-1")).thenReturn(crop);
        when(recognitionService.recognize(any())).thenReturn(new RecognitionEvidence());
        when(aggregationService.aggregate(any())).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceDetectionService);

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
                .setNews(null)
                .setImageMatches(null)
                .setWarnings(null);

        assertThat(response.getNews()).isEmpty();
        assertThat(response.getImageMatches()).isEmpty();
        assertThat(response.getWarnings()).isEmpty();
    }

    private ServiceFixture createFixture() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceDetectionService faceDetectionService = mock(FaceDetectionService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        doNothing().when(imageUtils).validateImage(image);
        return new ServiceFixture(
                new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService, faceDetectionService),
                image,
                imageUtils,
                recognitionService,
                aggregationService,
                faceDetectionService);
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
            FaceDetectionService faceDetectionService) {
    }
}

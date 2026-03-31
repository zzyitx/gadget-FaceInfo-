package com.example.face2info.service.impl;

import com.example.face2info.client.FaceCheckClient;
import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.FaceCheckMatchCandidate;
import com.example.face2info.entity.internal.FaceCheckUploadResponse;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Face2InfoServiceImplTest {

    @Test
    void shouldMapSummaryTagsAndWarningsToResponse() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate()
                        .setName("Jay Chou")
                        .setDescription("Mandopop singer")
                        .setSummary("Jay Chou is a major Mandopop artist.")
                        .setTags(java.util.List.of("Singer", "Producer")))
                .setWarnings(java.util.List.of("Summary provider unavailable")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getPerson().getSummary()).isEqualTo("Jay Chou is a major Mandopop artist.");
        assertThat(response.getPerson().getTags()).containsExactly("Singer", "Producer");
        assertThat(response.getWarnings()).containsExactly("Summary provider unavailable");
    }

    @Test
    void shouldMapRecognitionEvidenceAndAggregationResultToExistingResponseShape() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer.")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getPerson().getName()).isEqualTo("Jay Chou");
        assertThat(response.getPerson().getDescription()).isEqualTo("Jay Chou is a singer.");
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void shouldReturnPartialWhenAggregationContainsErrors() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer."))
                .setErrors(java.util.List.of("news fetch failed: timeout")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getStatus()).isEqualTo("partial");
        assertThat(response.getError()).contains("news fetch failed");
    }

    @Test
    void shouldReturnFailedWhenPersonCannotBeResolved() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence().setErrors(java.util.List.of("bing_images: timeout"));

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate())
                .setErrors(java.util.List.of("Unable to resolve person name from evidence")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getPerson()).isNull();
        assertThat(response.getError()).contains("Unable to resolve person name");
    }

    @Test
    void shouldKeepWarningsEmptyWhenAggregationDoesNotProvideWarnings() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou")));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient).process(image);

        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void shouldMapFacecheckMatchesIntoResponse() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer.")));
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse()
                .setItems(java.util.List.of(
                        new FaceCheckMatchCandidate()
                                .setImageDataUrl("data:image/jpeg;base64,AAA")
                                .setSimilarityScore(91.3)
                                .setSourceHost("x.com")
                                .setSourceUrl("https://x.com/demo")
                                .setGroup(2)
                                .setSeen(9)
                                .setIndex(1)
                )));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient).process(image);

        assertThat(response.getFacecheckMatches()).hasSize(1);
        assertThat(response.getFacecheckMatches().get(0).getSourceHost()).isEqualTo("x.com");
    }

    @Test
    void shouldKeepSuccessWhenFacecheckReturnsEmptyMatches() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer.")));
        when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient).process(image);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getFacecheckMatches()).isEmpty();
    }

    @Test
    void shouldDowngradeToPartialWhenFacecheckFailsButPersonExists() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer.")));
        when(faceCheckClient.upload(image)).thenThrow(new ApiCallException("facecheck timeout"));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService, faceCheckClient).process(image);

        assertThat(response.getStatus()).isEqualTo("partial");
        assertThat(response.getFacecheckMatches()).isEmpty();
        assertThat(response.getError()).contains("facecheck");
    }
}

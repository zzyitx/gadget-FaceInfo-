package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.ImageMatch;
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
    void shouldMapImageMatchesIntoResponse() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence().setImageMatches(java.util.List.of(
                new ImageMatch()
                        .setPosition(1)
                        .setTitle("Lei Jun official profile")
                        .setLink("https://example.com/official")
                        .setSource("Wikipedia")
                        .setThumbnailUrl("https://thumb.example.com/official.jpg")
                        .setSimilarityScore(96.4)
        ));

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Lei Jun").setDescription("Lei Jun is a technology entrepreneur.")));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService).process(image);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getImageMatches()).hasSize(1);
        assertThat(response.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/official.jpg");
        assertThat(response.getImageMatches().get(0).getSimilarityScore()).isEqualTo(96.4);
    }

    @Test
    void shouldMapSummaryTagsAndWarningsToResponse() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate()
                        .setName("Jay Chou")
                        .setDescription("Mandopop singer")
                        .setSummary("Jay Chou is a major Mandopop artist.")
                        .setTags(java.util.List.of("Singer", "Producer")))
                .setWarnings(java.util.List.of("Summary provider unavailable")));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService).process(image);

        assertThat(response.getPerson().getSummary()).isEqualTo("Jay Chou is a major Mandopop artist.");
        assertThat(response.getPerson().getTags()).containsExactly("Singer", "Producer");
        assertThat(response.getWarnings()).containsExactly("Summary provider unavailable");
    }

    @Test
    void shouldReturnPartialWhenAggregationContainsErrors() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer."))
                .setErrors(java.util.List.of("news fetch failed: timeout")));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService).process(image);

        assertThat(response.getStatus()).isEqualTo("partial");
        assertThat(response.getError()).contains("news fetch failed");
    }

    @Test
    void shouldReturnFailedWhenPersonCannotBeResolved() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence().setErrors(java.util.List.of("bing_images: timeout"));

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate())
                .setErrors(java.util.List.of("Unable to resolve person name from evidence")));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService).process(image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getPerson()).isNull();
        assertThat(response.getError()).contains("Unable to resolve person name");
    }

    @Test
    void shouldKeepWarningsEmptyWhenAggregationDoesNotProvideWarnings() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou")));

        FaceInfoResponse response = new Face2InfoServiceImpl(
                imageUtils, recognitionService, aggregationService).process(image);

        assertThat(response.getWarnings()).isEmpty();
    }
}

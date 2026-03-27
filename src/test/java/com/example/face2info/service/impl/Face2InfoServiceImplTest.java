package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
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
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate()
                        .setName("周杰伦")
                        .setDescription("华语流行男歌手")
                        .setSummary("周杰伦是华语流行乐代表人物。")
                        .setTags(java.util.List.of("歌手", "音乐制作人")))
                .setWarnings(java.util.List.of("正文智能处理暂时不可用")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getPerson().getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。");
        assertThat(response.getPerson().getTags()).containsExactly("歌手", "音乐制作人");
        assertThat(response.getWarnings()).containsExactly("正文智能处理暂时不可用");
    }

    @Test
    void shouldMapRecognitionEvidenceAndAggregationResultToExistingResponseShape() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer.")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService);

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
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        doNothing().when(imageUtils).validateImage(image);
        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer."))
                .setErrors(java.util.List.of("新闻获取失败: timeout")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getStatus()).isEqualTo("partial");
        assertThat(response.getError()).contains("新闻获取失败");
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
                .setErrors(java.util.List.of("未能从识别证据中解析人物名称")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getStatus()).isEqualTo("failed");
        assertThat(response.getPerson()).isNull();
        assertThat(response.getError()).contains("未能从识别证据中解析人物名称");
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
                .setPerson(new PersonAggregate().setName("周杰伦")));

        FaceInfoResponse response = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService).process(image);

        assertThat(response.getWarnings()).isEmpty();
    }
}

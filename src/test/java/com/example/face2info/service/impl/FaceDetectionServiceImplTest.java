package com.example.face2info.service.impl;

import com.example.face2info.client.FaceDetectionClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceDetectionProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import com.example.face2info.service.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceDetectionServiceImplTest {

    @Test
    void shouldEnhanceImageByUploadedUrlBeforeDetection() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        MinioService minioService = mock(MinioService.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile enhancedImage = new MockMultipartFile("image", "group-enhanced.jpg", "image/jpeg", new byte[]{7, 8, 9});

        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setPreviewImage("data:image/jpeg;base64,preview")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setConfidence(0.98)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(10).setY(20).setWidth(30).setHeight(40))
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(tmpfilesClient.uploadImage(image)).thenReturn("https://tmpfiles.org/image.jpg");
        when(summaryGenerationClient.enhanceFaceImageByUrl("https://tmpfiles.org/image.jpg", "group.jpg", "image/jpeg"))
                .thenReturn(enhancedImage);
        when(minioService.upload(any(byte[].class), any(String.class), any(String.class)))
                .thenReturn("http://192.168.216.133:9000/face-bucket/face-enhance/demo.jpg");
        when(client.detect(enhancedImage)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(
                client, summaryGenerationClient, tmpfilesClient, minioService, createProperties());
        DetectionSession stored = service.detect(image);

        assertThat(stored.getDetectionId()).isEqualTo("det-1");
        assertThat(stored.getFaces()).hasSize(1);
        assertThat(stored.getEnhancedImageUrl()).isEqualTo("http://192.168.216.133:9000/face-bucket/face-enhance/demo.jpg");
        assertThat(service.getSelectedFaceCrop("det-1", "face-1").getFilename()).isEqualTo("face-1.jpg");

        verify(tmpfilesClient).uploadImage(image);
        verify(summaryGenerationClient).enhanceFaceImageByUrl("https://tmpfiles.org/image.jpg", "group.jpg", "image/jpeg");
        verify(minioService).upload(any(byte[].class), any(String.class), any(String.class));
        verify(client).detect(enhancedImage);
    }

    @Test
    void shouldFallbackToOriginalImageWhenEnhanceFails() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        MinioService minioService = mock(MinioService.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(tmpfilesClient.uploadImage(image)).thenReturn("https://tmpfiles.org/image.jpg");
        when(summaryGenerationClient.enhanceFaceImageByUrl("https://tmpfiles.org/image.jpg", "group.jpg", "image/jpeg"))
                .thenThrow(new RuntimeException("enhance timeout"));
        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(
                client, summaryGenerationClient, tmpfilesClient, minioService, createProperties());
        DetectionSession stored = service.detect(image);

        assertThat(stored.getDetectionId()).isEqualTo("det-1");
        assertThat(stored.getEnhancedImageUrl()).isNull();
        verify(client).detect(image);
        verify(minioService, never()).upload(any(byte[].class), any(String.class), any(String.class));
    }

    @Test
    void shouldFailWhenSelectedFaceDoesNotExist() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        MinioService minioService = mock(MinioService.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(tmpfilesClient.uploadImage(image)).thenReturn("https://tmpfiles.org/image.jpg");
        when(summaryGenerationClient.enhanceFaceImageByUrl("https://tmpfiles.org/image.jpg", "group.jpg", "image/jpeg"))
                .thenReturn(image);
        when(minioService.upload(any(byte[].class), any(String.class), any(String.class)))
                .thenReturn("http://192.168.216.133:9000/face-bucket/face-enhance/demo.jpg");
        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(
                client, summaryGenerationClient, tmpfilesClient, minioService, createProperties());
        service.detect(image);

        assertThatThrownBy(() -> service.getSelectedFaceCrop("det-1", "face-x"))
                .isInstanceOf(FaceDetectionException.class);
    }

    @Test
    void shouldFailWhenSessionExpired() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        MinioService minioService = mock(MinioService.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(tmpfilesClient.uploadImage(image)).thenReturn("https://tmpfiles.org/image.jpg");
        when(summaryGenerationClient.enhanceFaceImageByUrl("https://tmpfiles.org/image.jpg", "group.jpg", "image/jpeg"))
                .thenReturn(image);
        when(minioService.upload(any(byte[].class), any(String.class), any(String.class)))
                .thenReturn("http://192.168.216.133:9000/face-bucket/face-enhance/demo.jpg");
        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(
                client, summaryGenerationClient, tmpfilesClient, minioService, createProperties());
        DetectionSession stored = service.detect(image);
        stored.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.getSelectedFaceCrop("det-1", "face-1"))
                .isInstanceOf(FaceDetectionException.class);
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setFaceDetection(new FaceDetectionProperties());
        properties.getApi().getFaceDetection().setSessionTtlSeconds(600);
        return properties;
    }
}

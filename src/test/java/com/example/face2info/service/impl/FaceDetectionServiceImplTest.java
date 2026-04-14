package com.example.face2info.service.impl;

import com.example.face2info.client.FaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceDetectionProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceDetectionServiceImplTest {

    @Test
    void shouldDetectUsingPreparedWorkingImage() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile original = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile enhancedImage = new MockMultipartFile("image", "group-enhanced.jpg", "image/jpeg", new byte[]{7, 8, 9});
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(original)
                .setWorkingImage(enhancedImage)
                .setUploadedImageUrl("https://tempfile.org/enhanced/preview")
                .setEnhancementApplied(true);

        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setPreviewImage("data:image/jpeg;base64,preview")
                .setUploadedImageUrl("https://tempfile.org/enhanced/preview")
                .setEnhancementApplied(true)
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setConfidence(0.98)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(10).setY(20).setWidth(30).setHeight(40))
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(enhancedImage)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(preparedImageResult);

        assertThat(stored.getDetectionId()).isEqualTo("det-1");
        assertThat(stored.getFaces()).hasSize(1);
        assertThat(stored.getUploadedImageUrl()).isEqualTo("https://tempfile.org/enhanced/preview");
        assertThat(stored.isEnhancementApplied()).isTrue();
        assertThat(service.getSelectedFaceCrop("det-1", "face-1").getFilename()).isEqualTo("face-1.jpg");

        verify(client).detect(enhancedImage);
    }

    @Test
    void shouldStoreEnhancementWarningIntoDetectionSession() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview")
                .setEnhancementApplied(false)
                .setWarning("图片高清化失败，已自动回退原图继续处理。");
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(preparedImageResult);

        assertThat(stored.getDetectionId()).isEqualTo("det-1");
        assertThat(stored.getUploadedImageUrl()).isEqualTo("https://tempfile.org/original/preview");
        assertThat(stored.isEnhancementApplied()).isFalse();
        assertThat(stored.getEnhancementWarning()).contains("高清化失败");
        verify(client).detect(image);
    }

    @Test
    void shouldFailWhenSelectedFaceDoesNotExist() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview");
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        service.detect(preparedImageResult);

        assertThatThrownBy(() -> service.getSelectedFaceCrop("det-1", "face-x"))
                .isInstanceOf(FaceDetectionException.class);
    }

    @Test
    void shouldFailWhenSessionExpired() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview");
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(preparedImageResult);
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

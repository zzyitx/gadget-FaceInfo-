package com.example.face2info.service.impl;

import com.example.face2info.client.FaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceDetectionProperties;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.SelectedFaceCrop;
import com.example.face2info.exception.FaceDetectionException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaceDetectionServiceImplTest {

    @Test
    void shouldStoreDetectionSessionAndReturnSelectedCrop() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setPreviewImage("data:image/jpeg;base64,preview")
                .setFaces(java.util.List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setConfidence(0.98)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(10).setY(20).setWidth(30).setHeight(40))
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(image);

        assertThat(stored.getDetectionId()).isEqualTo("det-1");
        assertThat(stored.getFaces()).hasSize(1);
        SelectedFaceCrop crop = service.getSelectedFaceCrop("det-1", "face-1");
        assertThat(crop.getFilename()).isEqualTo("face-1.jpg");
        assertThat(crop.getBytes()).containsExactly(9, 9, 9);
    }

    @Test
    void shouldFailWhenSelectedFaceDoesNotExist() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(java.util.List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        service.detect(image);

        assertThatThrownBy(() -> service.getSelectedFaceCrop("det-1", "face-x"))
                .isInstanceOf(FaceDetectionException.class)
                .hasMessageContaining("face_id");
    }

    @Test
    void shouldFailWhenSessionExpired() {
        FaceDetectionClient client = mock(FaceDetectionClient.class);
        MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DetectionSession session = new DetectionSession()
                .setDetectionId("det-1")
                .setFaces(java.util.List.of(new DetectedFace()
                        .setFaceId("face-1")
                        .setSelectedFaceCrop(new SelectedFaceCrop()
                                .setFilename("face-1.jpg")
                                .setContentType("image/jpeg")
                                .setBytes(new byte[]{9, 9, 9}))));

        when(client.detect(image)).thenReturn(session);

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(image);
        stored.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.getSelectedFaceCrop("det-1", "face-1"))
                .isInstanceOf(FaceDetectionException.class)
                .hasMessageContaining("expired");
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setFaceDetection(new FaceDetectionProperties());
        properties.getApi().getFaceDetection().setSessionTtlSeconds(600);
        return properties;
    }
}

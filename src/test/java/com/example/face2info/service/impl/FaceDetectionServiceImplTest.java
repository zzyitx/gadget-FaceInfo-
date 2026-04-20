package com.example.face2info.service.impl;

import com.example.face2info.client.CompreFaceDetectionClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.CompreFaceProperties;
import com.example.face2info.entity.internal.DetectedFace;
import com.example.face2info.entity.internal.DetectionSession;
import com.example.face2info.entity.internal.FaceBoundingBox;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.exception.FaceDetectionException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceDetectionServiceImplTest {

    @Test
    void shouldDetectUsingPreparedWorkingImage() throws IOException {
        CompreFaceDetectionClient client = mock(CompreFaceDetectionClient.class);
        MockMultipartFile original = createImage("group.jpg");
        MockMultipartFile enhancedImage = createImage("group-enhanced.jpg");
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(original)
                .setWorkingImage(enhancedImage)
                .setUploadedImageUrl("https://tempfile.org/enhanced/preview")
                .setEnhancementApplied(true);

        when(client.detect(enhancedImage)).thenReturn(List.of(
                new DetectedFace()
                        .setConfidence(0.98)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(10).setY(20).setWidth(30).setHeight(40))
        ));

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(preparedImageResult);

        assertThat(stored.getDetectionId()).isNotBlank();
        assertThat(stored.getPreviewImage()).startsWith("data:image/png;base64,");
        assertThat(stored.getFaces()).hasSize(1);
        assertThat(stored.getUploadedImageUrl()).isEqualTo("https://tempfile.org/enhanced/preview");
        assertThat(stored.isEnhancementApplied()).isTrue();
        assertThat(stored.getFaces().get(0).getFaceId()).startsWith(stored.getDetectionId() + "-face-");
        assertThat(service.getSelectedFaceCrop(stored.getDetectionId(), stored.getFaces().get(0).getFaceId()).getBytes()).isNotEmpty();

        verify(client).detect(enhancedImage);
    }

    @Test
    void shouldStoreEnhancementWarningIntoDetectionSession() throws IOException {
        CompreFaceDetectionClient client = mock(CompreFaceDetectionClient.class);
        MockMultipartFile image = createImage("group.jpg");
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview")
                .setEnhancementApplied(false)
                .setWarning("图片高清化失败，已自动回退原图继续处理。");

        when(client.detect(image)).thenReturn(List.of(
                new DetectedFace()
                        .setConfidence(0.88)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(5).setY(6).setWidth(25).setHeight(25))
        ));

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(preparedImageResult);

        assertThat(stored.getDetectionId()).isNotBlank();
        assertThat(stored.getUploadedImageUrl()).isEqualTo("https://tempfile.org/original/preview");
        assertThat(stored.isEnhancementApplied()).isFalse();
        assertThat(stored.getEnhancementWarning()).contains("高清化失败");
        verify(client).detect(image);
    }

    @Test
    void shouldFailWhenSelectedFaceDoesNotExist() throws IOException {
        CompreFaceDetectionClient client = mock(CompreFaceDetectionClient.class);
        MockMultipartFile image = createImage("group.jpg");
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview");

        when(client.detect(image)).thenReturn(List.of(
                new DetectedFace()
                        .setConfidence(0.88)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(5).setY(6).setWidth(25).setHeight(25))
        ));

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession session = service.detect(preparedImageResult);

        assertThatThrownBy(() -> service.getSelectedFaceCrop(session.getDetectionId(), "face-x"))
                .isInstanceOf(FaceDetectionException.class);
    }

    @Test
    void shouldFailWhenSessionExpired() throws IOException {
        CompreFaceDetectionClient client = mock(CompreFaceDetectionClient.class);
        MockMultipartFile image = createImage("group.jpg");
        PreparedImageResult preparedImageResult = new PreparedImageResult()
                .setOriginalImage(image)
                .setWorkingImage(image)
                .setUploadedImageUrl("https://tempfile.org/original/preview");

        when(client.detect(image)).thenReturn(List.of(
                new DetectedFace()
                        .setConfidence(0.88)
                        .setFaceBoundingBox(new FaceBoundingBox().setX(5).setY(6).setWidth(25).setHeight(25))
        ));

        FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, createProperties());
        DetectionSession stored = service.detect(preparedImageResult);
        stored.setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.getSelectedFaceCrop(stored.getDetectionId(), stored.getFaces().get(0).getFaceId()))
                .isInstanceOf(FaceDetectionException.class);
    }

    private ApiProperties createProperties() {
        ApiProperties properties = new ApiProperties();
        properties.getApi().setCompreface(new CompreFaceProperties());
        properties.getApi().getCompreface().setEnabled(true);
        properties.getApi().getCompreface().setReadTimeoutMs(30000);
        return properties;
    }

    private MockMultipartFile createImage(String fileName) throws IOException {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, (x < 40 ? Color.WHITE : Color.GRAY).getRGB());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return new MockMultipartFile("image", fileName, "image/png", outputStream.toByteArray());
    }
}

package com.example.face2info.service.impl;

import com.example.face2info.client.FaceEnhancementClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.PreparedImageResult;
import com.example.face2info.service.EnhancedImagePreparationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnhancedImagePreparationServiceImplTest {

    @Test
    void shouldUploadEnhancedImageWhenEnhancementSucceeds() {
        FaceEnhancementClient faceEnhancementClient = mock(FaceEnhancementClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        EnhancedImagePreparationService service = new EnhancedImagePreparationServiceImpl(
                faceEnhancementClient,
                tmpfilesClient
        );
        MockMultipartFile original = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile enhanced = new MockMultipartFile("image", "face-enhanced.jpg", "image/jpeg", new byte[]{9, 9, 9});

        when(faceEnhancementClient.enhanceFaceImage(original)).thenReturn(enhanced);
        when(tmpfilesClient.uploadImage(enhanced)).thenReturn("https://tempfile.org/enhanced/preview");

        PreparedImageResult result = service.prepare(original);

        assertThat(result.getOriginalImage()).isSameAs(original);
        assertThat(result.getWorkingImage()).isSameAs(enhanced);
        assertThat(result.getUploadedImageUrl()).isEqualTo("https://tempfile.org/enhanced/preview");
        assertThat(result.isEnhancementApplied()).isTrue();
        assertThat(result.getWarning()).isNull();
        verify(faceEnhancementClient).enhanceFaceImage(original);
        verify(tmpfilesClient).uploadImage(enhanced);
    }

    @Test
    void shouldFallbackToOriginalImageWhenEnhancementFails() {
        FaceEnhancementClient faceEnhancementClient = mock(FaceEnhancementClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        EnhancedImagePreparationService service = new EnhancedImagePreparationServiceImpl(
                faceEnhancementClient,
                tmpfilesClient
        );
        MockMultipartFile original = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(faceEnhancementClient.enhanceFaceImage(original)).thenThrow(new RuntimeException("GFPGAN timeout"));
        when(tmpfilesClient.uploadImage(original)).thenReturn("https://tempfile.org/original/preview");

        PreparedImageResult result = service.prepare(original);

        assertThat(result.getOriginalImage()).isSameAs(original);
        assertThat(result.getWorkingImage()).isSameAs(original);
        assertThat(result.getUploadedImageUrl()).isEqualTo("https://tempfile.org/original/preview");
        assertThat(result.isEnhancementApplied()).isFalse();
        assertThat(result.getWarning()).isEqualTo("图片高清化失败，已自动回退原图继续处理。");
        assertThat(result.getDebugMessage()).contains("GFPGAN timeout");
        verify(faceEnhancementClient).enhanceFaceImage(original);
        verify(tmpfilesClient).uploadImage(original);
    }

    @Test
    void shouldThrowWhenUploadFailsAfterFallback() {
        FaceEnhancementClient faceEnhancementClient = mock(FaceEnhancementClient.class);
        TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
        EnhancedImagePreparationService service = new EnhancedImagePreparationServiceImpl(
                faceEnhancementClient,
                tmpfilesClient
        );
        MockMultipartFile original = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(faceEnhancementClient.enhanceFaceImage(original)).thenThrow(new RuntimeException("GFPGAN timeout"));
        when(tmpfilesClient.uploadImage(original)).thenThrow(new RuntimeException("upload failed"));

        assertThatThrownBy(() -> service.prepare(original))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("upload failed");
    }
}

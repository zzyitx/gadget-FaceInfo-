package com.example.face2info.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片工具测试。
 */
class ImageUtilsTest {

    private final ImageUtils imageUtils = new ImageUtils();

    @Test
    void shouldValidateSupportedImage() {
        MockMultipartFile file = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThatCode(() -> imageUtils.validateImage(file)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsupportedImage() {
        MockMultipartFile file = new MockMultipartFile("image", "face.txt", "text/plain", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> imageUtils.validateImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持");
    }
}

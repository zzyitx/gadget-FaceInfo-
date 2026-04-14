package com.example.face2info.client.impl;

import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceEnhanceProperties;
import com.example.face2info.exception.ApiCallException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GfpganFaceEnhancementClientTest {

    @Test
    void shouldRunLocalGfpganCommandAndReadRestoredImage() throws IOException {
        Path projectDir = Files.createTempDirectory("gfpgan-project-");
        Files.writeString(projectDir.resolve("inference_gfpgan.py"), "print('stub')");

        ApiProperties properties = createProperties(projectDir);
        GfpganFaceEnhancementClient client = new GfpganFaceEnhancementClient(properties, new SuccessExecutor());
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        MultipartFile enhanced = client.enhanceFaceImageByUrl("https://example.com/face.jpg", image);

        assertThat(enhanced.getOriginalFilename()).isEqualTo("face-enhanced.jpg");
        assertThat(enhanced.getContentType()).isEqualTo("image/jpeg");
        assertThat(enhanced.getBytes()).containsExactly(new byte[]{9, 8, 7});
    }

    @Test
    void shouldFailWhenGfpganCommandReturnsNonZeroExitCode() throws IOException {
        Path projectDir = Files.createTempDirectory("gfpgan-project-");
        Files.writeString(projectDir.resolve("inference_gfpgan.py"), "print('stub')");

        ApiProperties properties = createProperties(projectDir);
        GfpganFaceEnhancementClient client = new GfpganFaceEnhancementClient(
                properties,
                (command, workingDirectory, timeout) -> new GfpganFaceEnhancementClient.CommandResult(1, "python error"));

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> client.enhanceFaceImageByUrl("https://example.com/face.jpg", image))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("exitCode=1")
                .hasMessageContaining("python error");
    }

    @Test
    void shouldFailWhenRestoredImageIsMissing() throws IOException {
        Path projectDir = Files.createTempDirectory("gfpgan-project-");
        Files.writeString(projectDir.resolve("inference_gfpgan.py"), "print('stub')");

        ApiProperties properties = createProperties(projectDir);
        GfpganFaceEnhancementClient client = new GfpganFaceEnhancementClient(
                properties,
                (command, workingDirectory, timeout) -> new GfpganFaceEnhancementClient.CommandResult(0, "done"));

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> client.enhanceFaceImageByUrl("https://example.com/face.jpg", image))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("未生成 restored_imgs 目录");
    }

    private ApiProperties createProperties(Path projectDir) {
        ApiProperties properties = new ApiProperties();
        FaceEnhanceProperties faceEnhance = properties.getApi().getFaceEnhance();
        faceEnhance.setEnabled(true);
        faceEnhance.setProvider("gfpgan");
        faceEnhance.getGfpgan().setProjectPath(projectDir.toString());
        faceEnhance.getGfpgan().setPythonCommand("python");
        faceEnhance.getGfpgan().setScriptPath("inference_gfpgan.py");
        faceEnhance.getGfpgan().setModelVersion("1.4");
        faceEnhance.getGfpgan().setUpscale(2);
        faceEnhance.getGfpgan().setProcessTimeoutMs(5000L);
        return properties;
    }

    private static final class SuccessExecutor implements GfpganFaceEnhancementClient.CommandExecutor {

        @Override
        public GfpganFaceEnhancementClient.CommandResult execute(List<String> command, Path workingDirectory, Duration timeout) {
            assertThat(command).contains("python");
            assertThat(command).contains("--bg_upsampler", "none");
            assertThat(command).contains("--ext", "auto");

            int outputIndex = command.indexOf("-o");
            int inputIndex = command.indexOf("-i");
            Path outputDir = Path.of(command.get(outputIndex + 1));
            Path inputFile = Path.of(command.get(inputIndex + 1));
            String inputFilename = inputFile.getFileName().toString();
            String basename = inputFilename.substring(0, inputFilename.lastIndexOf('.'));
            try {
                Path restoredDir = Files.createDirectories(outputDir.resolve("restored_imgs"));
                Files.write(restoredDir.resolve(basename + ".jpg"), new byte[]{9, 8, 7});
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return new GfpganFaceEnhancementClient.CommandResult(0, "Processing " + inputFilename);
        }
    }
}

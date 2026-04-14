package com.example.face2info.client.impl;

import com.example.face2info.client.FaceEnhancementClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.FaceEnhanceProperties;
import com.example.face2info.exception.ApiCallException;
import com.example.face2info.util.InMemoryMultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.face-enhance", name = "provider", havingValue = "gfpgan")
public class GfpganFaceEnhancementClient implements FaceEnhancementClient {

    private final ApiProperties properties;
    private final CommandExecutor commandExecutor;

    @Autowired
    public GfpganFaceEnhancementClient(ApiProperties properties) {
        this(properties, new ProcessBuilderCommandExecutor());
    }

    GfpganFaceEnhancementClient(ApiProperties properties, CommandExecutor commandExecutor) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
    }

    @Override
    public MultipartFile enhanceFaceImage(MultipartFile originalImage) {
        return doEnhance(originalImage);
    }

    @Override
    public MultipartFile enhanceFaceImageByUrl(String imageUrl, MultipartFile originalImage) {
        return doEnhance(originalImage);
    }

    private MultipartFile doEnhance(MultipartFile originalImage) {
        FaceEnhanceProperties faceEnhance = properties.getApi().getFaceEnhance();
        if (!faceEnhance.isEnabled()) {
            log.info("人脸高清化已禁用，跳过 GFPGAN 本地推理");
            return originalImage;
        }

        FaceEnhanceProperties.Gfpgan gfpgan = faceEnhance.getGfpgan();
        validateInput(originalImage, gfpgan);

        Path projectPath = resolveProjectPath(gfpgan);
        Path scriptPath = resolveScriptPath(projectPath, gfpgan);
        Path workingRoot = null;
        try {
            // 每次推理都在独立临时目录中完成，避免污染 GFPGAN 项目目录和并发请求互相覆盖。
            workingRoot = Files.createTempDirectory("gfpgan-run-");
            Path inputDir = Files.createDirectories(workingRoot.resolve("input"));
            Path outputDir = Files.createDirectories(workingRoot.resolve("output"));
            Path inputFile = inputDir.resolve(buildInputFilename(originalImage));
            Files.write(inputFile, originalImage.getBytes());

            List<String> command = buildCommand(gfpgan, scriptPath, inputFile, outputDir);
            log.info("开始调用 GFPGAN 本地高清化 projectPath={} scriptPath={} imageName={}",
                    projectPath, scriptPath, originalImage.getOriginalFilename());

            CommandResult result = commandExecutor.execute(command, projectPath, Duration.ofMillis(gfpgan.getProcessTimeoutMs()));
            if (result.exitCode() != 0) {
                throw new ApiCallException("GFPGAN 本地高清化失败，exitCode=" + result.exitCode()
                        + "，output=" + abbreviate(result.output()));
            }

            Path restoredFile = locateRestoredFile(outputDir, inputFile);
            byte[] restoredBytes = Files.readAllBytes(restoredFile);
            String contentType = detectContentType(restoredFile, originalImage);
            String filename = buildEnhancedFilename(originalImage.getOriginalFilename(), restoredFile);
            return new InMemoryMultipartFile(filename, contentType, restoredBytes);
        } catch (IOException ex) {
            throw new ApiCallException("GFPGAN 本地高清化失败：" + ex.getMessage(), ex);
        } finally {
            deleteQuietly(workingRoot);
        }
    }

    private void validateInput(MultipartFile originalImage, FaceEnhanceProperties.Gfpgan gfpgan) {
        if (originalImage == null || originalImage.isEmpty()) {
            throw new ApiCallException("GFPGAN 本地高清化失败，originalImage 不能为空");
        }
        if (!StringUtils.hasText(gfpgan.getProjectPath())) {
            throw new ApiCallException("GFPGAN 本地高清化失败，projectPath 未配置");
        }
        if (!StringUtils.hasText(gfpgan.getPythonCommand())) {
            throw new ApiCallException("GFPGAN 本地高清化失败，pythonCommand 未配置");
        }
        if (!StringUtils.hasText(gfpgan.getScriptPath())) {
            throw new ApiCallException("GFPGAN 本地高清化失败，scriptPath 未配置");
        }
    }

    private Path resolveProjectPath(FaceEnhanceProperties.Gfpgan gfpgan) {
        Path projectPath = Path.of(gfpgan.getProjectPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectPath)) {
            throw new ApiCallException("GFPGAN 本地高清化失败，projectPath 不存在：" + projectPath);
        }
        return projectPath;
    }

    private Path resolveScriptPath(Path projectPath, FaceEnhanceProperties.Gfpgan gfpgan) {
        Path scriptPath = Path.of(gfpgan.getScriptPath());
        if (!scriptPath.isAbsolute()) {
            scriptPath = projectPath.resolve(scriptPath);
        }
        scriptPath = scriptPath.normalize();
        if (!Files.isRegularFile(scriptPath)) {
            throw new ApiCallException("GFPGAN 本地高清化失败，scriptPath 不存在：" + scriptPath);
        }
        return scriptPath;
    }

    private List<String> buildCommand(FaceEnhanceProperties.Gfpgan gfpgan,
                                      Path scriptPath,
                                      Path inputFile,
                                      Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(gfpgan.getPythonCommand());
        command.add(scriptPath.toString());
        command.add("-i");
        command.add(inputFile.toString());
        command.add("-o");
        command.add(outputDir.toString());
        command.add("-v");
        command.add(gfpgan.getModelVersion());
        command.add("-s");
        command.add(String.valueOf(gfpgan.getUpscale()));
        command.add("--bg_upsampler");
        command.add(defaultIfBlank(gfpgan.getBackgroundUpsampler(), "none"));
        command.add("--ext");
        command.add(defaultIfBlank(gfpgan.getOutputExtension(), "auto"));
        command.add("-w");
        command.add(String.valueOf(gfpgan.getWeight()));
        if (gfpgan.isOnlyCenterFace()) {
            command.add("--only_center_face");
        }
        if (gfpgan.isAligned()) {
            command.add("--aligned");
        }
        return command;
    }

    private Path locateRestoredFile(Path outputDir, Path inputFile) throws IOException {
        Path restoredDir = outputDir.resolve("restored_imgs");
        if (!Files.isDirectory(restoredDir)) {
            throw new ApiCallException("GFPGAN 本地高清化失败，未生成 restored_imgs 目录");
        }
        String filename = inputFile.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        String basename = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(restoredDir, basename + ".*")) {
            for (Path candidate : stream) {
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        throw new ApiCallException("GFPGAN 本地高清化失败，未找到输出文件，basename=" + basename);
    }

    private String detectContentType(Path restoredFile, MultipartFile originalImage) throws IOException {
        String contentType = Files.probeContentType(restoredFile);
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        if (StringUtils.hasText(originalImage.getContentType())) {
            return originalImage.getContentType();
        }
        String filename = restoredFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private String buildInputFilename(MultipartFile originalImage) {
        String originalFilename = originalImage.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            return "source.jpg";
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String buildEnhancedFilename(String originalFilename, Path restoredFile) {
        String extension = "";
        String restoredName = restoredFile.getFileName().toString();
        int restoredDotIndex = restoredName.lastIndexOf('.');
        if (restoredDotIndex >= 0) {
            extension = restoredName.substring(restoredDotIndex);
        }
        if (!StringUtils.hasText(originalFilename)) {
            return "enhanced-face" + extension;
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return originalFilename + "-enhanced" + extension;
        }
        return originalFilename.substring(0, dotIndex) + "-enhanced" + extension;
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace(System.lineSeparator(), " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void deleteQuietly(Path workingRoot) {
        if (workingRoot == null || !Files.exists(workingRoot)) {
            return;
        }
        try {
            Files.walk(workingRoot)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            log.debug("清理 GFPGAN 临时目录失败 path={} error={}", path, ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            log.debug("遍历 GFPGAN 临时目录失败 path={} error={}", workingRoot, ex.getMessage());
        }
    }

    interface CommandExecutor {
        CommandResult execute(List<String> command, Path workingDirectory, Duration timeout);
    }

    record CommandResult(int exitCode, String output) {
    }

    static final class ProcessBuilderCommandExecutor implements CommandExecutor {

        @Override
        public CommandResult execute(List<String> command, Path workingDirectory, Duration timeout) {
            Process process = null;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(workingDirectory.toFile());
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new ApiCallException("GFPGAN 本地高清化超时，timeoutMs=" + timeout.toMillis());
                }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return new CommandResult(process.exitValue(), output);
            } catch (IOException ex) {
                throw new ApiCallException("GFPGAN 本地高清化启动失败：" + ex.getMessage(), ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiCallException("GFPGAN 本地高清化执行被中断", ex);
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }
}

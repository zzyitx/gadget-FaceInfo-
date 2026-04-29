package com.example.face2info.client.impl;

import com.example.face2info.client.MaigretClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.MaigretProperties;
import com.example.face2info.entity.response.SocialAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Component
public class MaigretClientImpl implements MaigretClient {

    private static final String SOURCE = "maigret";

    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public MaigretClientImpl(ApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SocialAccount> findSuspectedAccounts(String username) {
        MaigretProperties maigret = properties.getApi().getMaigret();
        if (!maigret.isEnabled() || !StringUtils.hasText(username)) {
            return List.of();
        }
        Path outputDirectory = null;
        try {
            outputDirectory = Files.createTempDirectory("face2info-maigret-");
            List<String> command = buildCommand(maigret, username, outputDirectory);
            // Maigret 只能按用户名枚举站点，输出必须先落到临时目录，再解析为疑似账号。
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .directory(outputDirectory.toFile())
                    .start();
            boolean finished = process.waitFor(maigret.getProcessTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Maigret 查询超时 username={} timeoutMs={}", username, maigret.getProcessTimeoutMs());
                return List.of();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                log.warn("Maigret 查询失败 username={} exitCode={} outputPreview={}",
                        username, process.exitValue(), preview(output));
                return List.of();
            }
            return readAccounts(outputDirectory, username, maigret.getMaxAccountsPerUsername());
        } catch (IOException ex) {
            log.warn("Maigret 命令不可用或输出读取失败 username={} error={}", username, ex.getMessage(), ex);
            return List.of();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Maigret 查询被中断 username={} error={}", username, ex.getMessage(), ex);
            return List.of();
        } finally {
            cleanup(outputDirectory);
        }
    }

    private List<String> buildCommand(MaigretProperties maigret, String username, Path outputDirectory) {
        List<String> command = new ArrayList<>();
        if (maigret.getCommandPrefix() != null && !maigret.getCommandPrefix().isEmpty()) {
            command.addAll(maigret.getCommandPrefix());
        } else {
            command.add(maigret.getExecutable());
        }
        command.add(username);
        command.add("--json");
        command.add("simple");
        command.add("--folderoutput");
        command.add(outputDirectory.toString());
        command.add("--top-sites");
        command.add(String.valueOf(maigret.getTopSites()));
        command.add("--timeout");
        command.add(String.valueOf(maigret.getSiteTimeoutSeconds()));
        if (maigret.isNoRecursion()) {
            command.add("--no-recursion");
        }
        if (maigret.isNoAutoupdate()) {
            command.add("--no-autoupdate");
        }
        return command;
    }

    private List<SocialAccount> readAccounts(Path outputDirectory, String username, int limit) throws IOException {
        Map<String, SocialAccount> accounts = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            List<Path> jsonFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path jsonFile : jsonFiles) {
                JsonNode root = objectMapper.readTree(jsonFile.toFile());
                collectAccounts(root, null, username, accounts);
                if (accounts.size() >= limit) {
                    break;
                }
            }
        }
        return accounts.values().stream().limit(limit).toList();
    }

    private void collectAccounts(JsonNode node,
                                 String platformHint,
                                 String username,
                                 Map<String, SocialAccount> accounts) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            SocialAccount account = toAccount(node, platformHint, username);
            if (account != null) {
                accounts.putIfAbsent(account.getUrl(), account);
            }
            node.fields().forEachRemaining(entry -> collectAccounts(entry.getValue(), entry.getKey(), username, accounts));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectAccounts(item, platformHint, username, accounts);
            }
        }
    }

    private SocialAccount toAccount(JsonNode node, String platformHint, String username) {
        String url = firstNonBlank(
                text(node, "url"),
                text(node, "profile_url"),
                text(node, "profileUrl"),
                text(node, "link")
        );
        if (!StringUtils.hasText(url) || !isClaimed(node)) {
            return null;
        }
        String platform = firstNonBlank(text(node, "site"), text(node, "name"), platformHint);
        // 用户名枚举缺少人物身份校验，所有 Maigret 命中都只能作为疑似结果返回。
        return new SocialAccount()
                .setPlatform(normalizePlatform(platform))
                .setUrl(url.trim())
                .setUsername(firstNonBlank(text(node, "username"), username))
                .setSource(SOURCE)
                .setSuspected(true)
                .setConfidence("suspected");
    }

    private boolean isClaimed(JsonNode node) {
        String status = firstNonBlank(text(node, "status"), text(node, "status_string"), text(node, "statusString"));
        if (!StringUtils.hasText(status)) {
            return true;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        return normalized.contains("claimed")
                || normalized.contains("found")
                || normalized.contains("exists");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText(null) : null;
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return "maigret";
        }
        return platform.trim().replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String preview(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String normalized = output.replaceAll("\\s+", " ").trim();
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }

    private void cleanup(Path outputDirectory) {
        if (outputDirectory == null || !Files.exists(outputDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.debug("Maigret 临时文件清理失败 path={} error={}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.debug("Maigret 临时目录清理失败 path={} error={}", outputDirectory, ex.getMessage());
        }
    }
}

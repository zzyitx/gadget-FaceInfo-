package com.example.face2info.client.impl;

import com.example.face2info.client.OsintSocialAccountClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.config.MaigretProperties;
import com.example.face2info.entity.response.SocialAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Component
public class LocalOsintSocialAccountClientImpl implements OsintSocialAccountClient {

    private static final String MAIGRET_SOURCE = "maigret";
    private static final String SHERLOCK_SOURCE = "sherlock";
    private static final String TOOKIE_SOURCE = "tookie-osint";

    private final ApiProperties properties;
    private final ObjectMapper objectMapper;
    private final Set<String> unavailableTools = new HashSet<>();

    public LocalOsintSocialAccountClientImpl(ApiProperties properties, ObjectMapper objectMapper) {
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
            outputDirectory = Files.createTempDirectory("face2info-osint-");
            Map<String, SocialAccount> accounts = new LinkedHashMap<>();
            runMaigret(username, maigret, outputDirectory).forEach(account -> addAccount(accounts, account));
            runSherlock(username, maigret, outputDirectory).forEach(account -> addAccount(accounts, account));
            runTookie(username, maigret, outputDirectory).forEach(account -> addAccount(accounts, account));
            return accounts.values().stream()
                    .limit(maigret.getMaxAccountsPerUsername())
                    .toList();
        } catch (IOException ex) {
            log.warn("用户名 OSINT 临时目录创建或读取失败 username={} error={}", username, ex.getMessage(), ex);
            return List.of();
        } finally {
            cleanup(outputDirectory);
        }
    }

    private List<SocialAccount> runMaigret(String username, MaigretProperties maigret, Path outputDirectory) {
        if (isToolUnavailable(MAIGRET_SOURCE)) {
            return List.of();
        }
        Path toolOutput = outputDirectory.resolve("maigret");
        try {
            Files.createDirectories(toolOutput);
            List<String> sites = resolveSupportedSiteNames(
                    Path.of(maigret.getProjectPath(), "maigret", "resources", "data.json"),
                    "/sites",
                    maigret.getSocialSites(),
                    maigret.getTopSites()
            );
            if (sites.isEmpty()) {
                log.warn("Maigret 未匹配到可用社交站点，跳过 username={}", username);
                return List.of();
            }
            List<String> command = baseCommand(maigret.getCommandPrefix(), maigret.getExecutable());
            command.add(username);
            command.add("--json");
            command.add("simple");
            command.add("--folderoutput");
            command.add(toolOutput.toString());
            command.add("--timeout");
            command.add(String.valueOf(maigret.getSiteTimeoutSeconds()));
            for (String site : sites) {
                command.add("--site");
                command.add(site);
            }
            if (maigret.isNoRecursion()) {
                command.add("--no-recursion");
            }
            if (maigret.isNoAutoupdate()) {
                command.add("--no-autoupdate");
            }
            if (!runProcess(MAIGRET_SOURCE, command, resolveWorkingDirectory(maigret.getProjectPath(), toolOutput),
                    maigret.getProcessTimeoutMs(), username)) {
                return List.of();
            }
            return readMaigretAccounts(toolOutput, username, maigret.getMaxAccountsPerUsername());
        } catch (IOException | InterruptedException ex) {
            return handleToolFailure(MAIGRET_SOURCE, username, ex);
        }
    }

    private List<SocialAccount> runSherlock(String username, MaigretProperties maigret, Path outputDirectory) {
        MaigretProperties.Tool sherlock = maigret.getSherlock();
        if (sherlock == null || !sherlock.isEnabled() || isToolUnavailable(SHERLOCK_SOURCE)) {
            return List.of();
        }
        Path toolOutput = outputDirectory.resolve("sherlock");
        try {
            Files.createDirectories(toolOutput);
            List<String> sites = resolveSupportedSiteNames(
                    Path.of(sherlock.getProjectPath(), "sherlock_project", "resources", "data.json"),
                    "",
                    maigret.getSocialSites(),
                    maigret.getTopSites()
            );
            if (sites.isEmpty()) {
                log.warn("Sherlock 未匹配到可用社交站点，跳过 username={}", username);
                return List.of();
            }
            List<String> command = baseCommand(sherlock.getCommandPrefix(), sherlock.getExecutable());
            command.add(username);
            command.add("--csv");
            command.add("--folderoutput");
            command.add(toolOutput.toString());
            command.add("--timeout");
            command.add(String.valueOf(maigret.getSiteTimeoutSeconds()));
            command.add("--print-found");
            command.add("--no-color");
            command.add("--local");
            for (String site : sites) {
                command.add("--site");
                command.add(site);
            }
            if (!runProcess(SHERLOCK_SOURCE, command, resolveWorkingDirectory(sherlock.getProjectPath(), toolOutput),
                    maigret.getProcessTimeoutMs(), username)) {
                return List.of();
            }
            return readSherlockAccounts(toolOutput, username, maigret.getMaxAccountsPerUsername());
        } catch (IOException | InterruptedException ex) {
            return handleToolFailure(SHERLOCK_SOURCE, username, ex);
        }
    }

    private List<SocialAccount> runTookie(String username, MaigretProperties maigret, Path outputDirectory) {
        MaigretProperties.Tool tookie = maigret.getTookie();
        if (tookie == null || !tookie.isEnabled() || isToolUnavailable(TOOKIE_SOURCE)) {
            return List.of();
        }
        Path toolOutput = outputDirectory.resolve("tookie");
        try {
            Files.createDirectories(toolOutput);
            Path limitedProject = prepareLimitedTookieProject(tookie, toolOutput, maigret.getSocialSites(), maigret.getTopSites());
            if (limitedProject == null) {
                log.warn("Tookie 未匹配到可用社交站点，跳过 username={}", username);
                return List.of();
            }
            List<String> command = baseCommand(tookie.getCommandPrefix(), tookie.getExecutable());
            command.add(limitedProject.resolve("face2info_tookie_runner.py").toString());
            command.add("-u");
            command.add(username);
            command.add("-o");
            command.add("json");
            command.add("-t");
            command.add("2");
            command.add("-sk");
            if (!runProcess(TOOKIE_SOURCE, command, limitedProject, maigret.getProcessTimeoutMs(), username)) {
                return List.of();
            }
            return readTookieAccounts(limitedProject, username, maigret.getMaxAccountsPerUsername());
        } catch (IOException | InterruptedException ex) {
            return handleToolFailure(TOOKIE_SOURCE, username, ex);
        }
    }

    private boolean runProcess(String source,
                               List<String> command,
                               Path workingDirectory,
                               int timeoutMs,
                               String username) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .directory(workingDirectory.toFile())
                    .start();
        } catch (IOException ex) {
            if (isMissingCommand(ex)) {
                unavailableTools.add(source);
                log.warn("{} 命令不可用，后续用户名候选将跳过 username={} command={} error={}",
                        source, username, configuredCommand(command), ex.getMessage());
                return false;
            }
            throw ex;
        }
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("{} 查询超时 username={} timeoutMs={}", source, username, timeoutMs);
            return false;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            log.warn("{} 查询失败 username={} exitCode={} outputPreview={}",
                    source, username, process.exitValue(), preview(output));
            return false;
        }
        return true;
    }

    private Path prepareLimitedTookieProject(MaigretProperties.Tool tookie,
                                             Path toolOutput,
                                             List<String> allowedSites,
                                             int limit) throws IOException {
        Path sourceProject = Path.of(tookie.getProjectPath()).toAbsolutePath().normalize();
        Path limitedProject = toolOutput.resolve("project");
        copyDirectory(sourceProject.resolve("modules"), limitedProject.resolve("modules"));
        copyDirectory(sourceProject.resolve("config"), limitedProject.resolve("config"));
        Files.createDirectories(limitedProject.resolve("sites"));
        Path fields = sourceProject.resolve("sites").resolve("feilds.json");
        if (Files.exists(fields)) {
            Files.copy(fields, limitedProject.resolve("sites").resolve("feilds.json"), StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.writeString(limitedProject.resolve("sites").resolve("feilds.json"), "{}", StandardCharsets.UTF_8);
        }
        Files.writeString(limitedProject.resolve("sites").resolve("headers.txt"), "", StandardCharsets.UTF_8);
        Files.copy(sourceProject.resolve(tookie.getScriptPath()), limitedProject.resolve("brib.py"), StandardCopyOption.REPLACE_EXISTING);
        JsonNode filteredSites = filterTookieSites(sourceProject.resolve("sites").resolve("sites.json"), allowedSites, limit);
        if (filteredSites == null || !filteredSites.isArray() || filteredSites.isEmpty()) {
            return null;
        }
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(limitedProject.resolve("sites").resolve("sites.json").toFile(), filteredSites);
        Files.writeString(limitedProject.resolve("face2info_tookie_runner.py"), """
                import runpy
                import modules.modules as tookie_modules

                tookie_modules.check_update = lambda: (False, "local")
                tookie_modules.get_header_file = lambda debug=False: None
                runpy.run_path("brib.py", run_name="__main__")
                """, StandardCharsets.UTF_8);
        return limitedProject;
    }

    private JsonNode filterTookieSites(Path sitesFile, List<String> allowedSites, int limit) throws IOException {
        JsonNode root = objectMapper.readTree(sitesFile.toFile());
        if (!root.isArray()) {
            return objectMapper.createArrayNode();
        }
        Set<String> allowed = normalizedAllowedSites(allowedSites);
        var result = objectMapper.createArrayNode();
        for (JsonNode site : root) {
            String url = text(site, "site");
            if (matchesAllowedSite(url, allowed)) {
                result.add(site);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private List<String> resolveSupportedSiteNames(Path databaseFile,
                                                   String sitesPointer,
                                                   List<String> allowedSites,
                                                   int limit) throws IOException {
        JsonNode root = objectMapper.readTree(databaseFile.toFile());
        JsonNode sitesNode = StringUtils.hasText(sitesPointer) ? root.at(sitesPointer) : root;
        if (!sitesNode.isObject()) {
            return List.of();
        }
        Set<String> allowed = normalizedAllowedSites(allowedSites);
        List<String> matched = new ArrayList<>();
        sitesNode.fieldNames().forEachRemaining(siteName -> {
            if (matched.size() < limit && matchesAllowedSite(siteName, allowed)) {
                matched.add(siteName);
            }
        });
        return matched;
    }

    private Set<String> normalizedAllowedSites(List<String> allowedSites) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        List<String> source = allowedSites == null ? List.of() : allowedSites;
        for (String site : source) {
            String value = normalizeSiteKey(site);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private boolean matchesAllowedSite(String candidate, Set<String> allowed) {
        String normalized = normalizeSiteKey(candidate);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        for (String site : allowed) {
            if (normalized.equals(site)) {
                return true;
            }
            if (site.length() > 2 && normalized.contains(site)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSiteKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String source = value.trim().toLowerCase(Locale.ROOT);
        if (source.startsWith("http")) {
            try {
                String host = URI.create(source).getHost();
                source = host == null ? source : host;
            } catch (IllegalArgumentException ignored) {
                // 站点配置里可能是模板 URL，解析失败时回退到字符串归一化。
            }
        }
        return source.replace("www.", "")
                .replace("m.", "")
                .replace(".com", "")
                .replace(".org", "")
                .replace(".net", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private List<SocialAccount> readMaigretAccounts(Path outputDirectory, String username, int limit) throws IOException {
        Map<String, SocialAccount> accounts = new LinkedHashMap<>();
        for (Path jsonFile : listFiles(outputDirectory, ".json")) {
            JsonNode root = objectMapper.readTree(jsonFile.toFile());
            collectMaigretAccounts(root, null, username, accounts);
            if (accounts.size() >= limit) {
                break;
            }
        }
        return accounts.values().stream().limit(limit).toList();
    }

    private void collectMaigretAccounts(JsonNode node,
                                        String platformHint,
                                        String username,
                                        Map<String, SocialAccount> accounts) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            SocialAccount account = toAccount(
                    MAIGRET_SOURCE,
                    firstNonBlank(text(node, "site"), text(node, "name"), platformHint),
                    firstNonBlank(text(node, "username"), username),
                    firstNonBlank(text(node, "url"), text(node, "profile_url"), text(node, "profileUrl"), text(node, "link")),
                    isClaimed(node)
            );
            if (account != null) {
                addAccount(accounts, account);
            }
            node.fields().forEachRemaining(entry -> collectMaigretAccounts(entry.getValue(), entry.getKey(), username, accounts));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectMaigretAccounts(item, platformHint, username, accounts);
            }
        }
    }

    private List<SocialAccount> readSherlockAccounts(Path outputDirectory, String username, int limit) throws IOException {
        Map<String, SocialAccount> accounts = new LinkedHashMap<>();
        for (Path csvFile : listFiles(outputDirectory, ".csv")) {
            List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size() && accounts.size() < limit; i++) {
                String[] columns = lines.get(i).split(",", -1);
                if (columns.length < 5 || !"Claimed".equalsIgnoreCase(columns[4].trim())) {
                    continue;
                }
                SocialAccount account = toAccount(SHERLOCK_SOURCE, columns[1], username, columns[3], true);
                if (account != null) {
                    addAccount(accounts, account);
                }
            }
        }
        return accounts.values().stream().limit(limit).toList();
    }

    private List<SocialAccount> readTookieAccounts(Path outputDirectory, String username, int limit) throws IOException {
        Map<String, SocialAccount> accounts = new LinkedHashMap<>();
        String safeUsername = username.replaceAll("[^A-Za-z0-9._-]", "_")
                .replace("..", "_")
                .replaceFirst("^\\.+", "");
        if (!StringUtils.hasText(safeUsername)) {
            safeUsername = "output";
        }
        Path jsonFile = outputDirectory.resolve(safeUsername.substring(0, Math.min(safeUsername.length(), 128)) + ".json");
        if (!Files.exists(jsonFile)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        if (!root.isArray()) {
            return List.of();
        }
        for (JsonNode item : root) {
            if (accounts.size() >= limit) {
                break;
            }
            if (!item.path("found").asBoolean(false)) {
                continue;
            }
            String url = text(item, "url");
            SocialAccount account = toAccount(TOOKIE_SOURCE, platformFromUrl(url), username, url, true);
            if (account != null) {
                addAccount(accounts, account);
            }
        }
        return accounts.values().stream().limit(limit).toList();
    }

    private SocialAccount toAccount(String source, String platform, String username, String url, boolean claimed) {
        if (!claimed || !StringUtils.hasText(url)) {
            return null;
        }
        return new SocialAccount()
                .setPlatform(normalizePlatform(platformFromUrl(firstNonBlank(platform, url))))
                .setUrl(url.trim())
                .setUsername(username)
                .setSource(source)
                .setSuspected(true)
                .setConfidence("suspected");
    }

    private void addAccount(Map<String, SocialAccount> accounts, SocialAccount account) {
        if (account == null || !StringUtils.hasText(account.getUrl())) {
            return;
        }
        accounts.putIfAbsent(normalizeSocialAccountKey(account), account);
    }

    private String normalizeSocialAccountKey(SocialAccount account) {
        String url = normalizeDedupUrl(account.getUrl());
        if (StringUtils.hasText(url)) {
            return url;
        }
        return (cleanKeyPart(account.getPlatform()) + ":" + cleanKeyPart(account.getUsername())).toLowerCase(Locale.ROOT);
    }

    private String normalizeDedupUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return cleanUrlTail(trimmed.toLowerCase(Locale.ROOT));
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.startsWith("www.")) {
                normalizedHost = normalizedHost.substring(4);
            }
            String path = cleanUrlTail(uri.getPath());
            String query = StringUtils.hasText(uri.getQuery()) ? "?" + uri.getQuery() : "";
            return normalizedHost + path + query;
        } catch (IllegalArgumentException ex) {
            return cleanUrlTail(trimmed.toLowerCase(Locale.ROOT));
        }
    }

    private String cleanUrlTail(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value;
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String cleanKeyPart(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
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

    private String platformFromUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (!value.startsWith("http")) {
            return value;
        }
        try {
            String host = URI.create(value).getHost();
            if (!StringUtils.hasText(host)) {
                return value;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("www.")) {
                normalized = normalized.substring(4);
            }
            if (normalized.startsWith("m.")) {
                normalized = normalized.substring(2);
            }
            int dot = normalized.indexOf('.');
            return dot > 0 ? normalized.substring(0, dot) : normalized;
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return "osint";
        }
        return platform.trim().replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
    }

    private List<Path> listFiles(Path directory, String suffix) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path resolveWorkingDirectory(String projectPath, Path outputDirectory) {
        if (StringUtils.hasText(projectPath)) {
            Path path = Path.of(projectPath).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
            log.warn("OSINT 工具项目目录不可用，回退到临时输出目录 projectPath={}", path);
        }
        return outputDirectory;
    }

    private List<String> baseCommand(List<String> commandPrefix, String executable) {
        List<String> command = new ArrayList<>();
        if (commandPrefix != null && !commandPrefix.isEmpty()) {
            command.addAll(commandPrefix);
        } else {
            command.add(executable);
        }
        return command;
    }

    private boolean isToolUnavailable(String source) {
        return unavailableTools.contains(source);
    }

    private List<SocialAccount> handleToolFailure(String source, String username, Exception ex) {
        if (ex instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        log.warn("{} 查询不可用 username={} error={}", source, username, ex.getMessage(), ex);
        return List.of();
    }

    private boolean isMissingCommand(IOException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("createprocess error=2")
                || normalized.contains("no such file or directory")
                || normalized.contains("cannot run program");
    }

    private String configuredCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        return String.join(" ", command);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText(null) : null;
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
                    log.debug("OSINT 临时文件清理失败 path={} error={}", path, ex.getMessage());
                }
            });
        } catch (IOException ex) {
            log.debug("OSINT 临时目录清理失败 path={} error={}", outputDirectory, ex.getMessage());
        }
    }
}

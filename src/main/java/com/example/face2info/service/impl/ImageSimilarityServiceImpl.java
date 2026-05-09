package com.example.face2info.service.impl;

import com.example.face2info.client.CompreFaceVerificationClient;
import com.example.face2info.config.ApiProperties;
import com.example.face2info.service.ImageSimilarityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ImageSimilarityServiceImpl implements ImageSimilarityService {

    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_IMAGE_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PAGE_DOWNLOAD_BYTES = 1024 * 1024;
    private static final int MAX_PAGE_IMAGE_CANDIDATES = 8;
    private static final Pattern META_IMAGE_PATTERN = Pattern.compile(
            "<meta\\b[^>]*(?:property|name)\\s*=\\s*['\"](?:og:image|twitter:image|twitter:image:src)['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern META_IMAGE_REVERSED_PATTERN = Pattern.compile(
            "<meta\\b[^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*(?:property|name)\\s*=\\s*['\"](?:og:image|twitter:image|twitter:image:src)['\"][^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
            "<img\\b[^>]*\\bsrc\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private final CompreFaceVerificationClient compreFaceVerificationClient;
    private final ApiProperties properties;

    @Autowired
    public ImageSimilarityServiceImpl(CompreFaceVerificationClient compreFaceVerificationClient,
                                      ApiProperties properties) {
        this.compreFaceVerificationClient = compreFaceVerificationClient;
        this.properties = properties;
    }

    ImageSimilarityServiceImpl(CompreFaceVerificationClient compreFaceVerificationClient) {
        this(compreFaceVerificationClient, new ApiProperties());
    }

    @Override
    public double score(MultipartFile originalImage, String candidateImageUrl, double fallbackScore) {
        if (!StringUtils.hasText(candidateImageUrl) || originalImage == null || originalImage.isEmpty()) {
            return fallbackScore;
        }
        if (!isComprefaceEnabled()) {
            log.debug("CompreFace verification 已禁用，使用回退相似度分数。");
            return fallbackScore;
        }
        try {
            List<CandidateImage> candidateImages = resolveCandidateImages(candidateImageUrl);
            byte[] originalBytes = originalImage.getBytes();
            double bestScore = Double.NaN;
            for (CandidateImage candidateImage : candidateImages) {
                try {
                    OptionalDouble similarity = compreFaceVerificationClient.verify(
                            originalBytes,
                            candidateImage.content(),
                            resolveContentType(originalImage)
                    );
                    if (similarity.isPresent()) {
                        bestScore = Math.max(Double.isNaN(bestScore) ? 0.0D : bestScore, similarity.getAsDouble() * 100.0D);
                    }
                } catch (Exception ex) {
                    log.debug("候选图片人脸比对失败，继续尝试下一张 imageUrl={} error={}",
                            candidateImage.url(), ex.getMessage());
                }
            }
            if (!Double.isNaN(bestScore)) {
                return round(bestScore);
            }
        } catch (Exception ex) {
            log.debug("图像相似度计算失败，使用回退分 url={} error={}", candidateImageUrl, ex.getMessage());
        }
        return fallbackScore;
    }

    private List<CandidateImage> resolveCandidateImages(String candidateImageUrl) throws IOException {
        DownloadedResource resource = readFromUrl(candidateImageUrl, MAX_IMAGE_DOWNLOAD_BYTES);
        if (resource.content().length == 0) {
            return List.of();
        }
        if (isImageResource(candidateImageUrl, resource)) {
            return List.of(new CandidateImage(candidateImageUrl, resource.content()));
        }
        if (!isHtmlResource(candidateImageUrl, resource)) {
            return List.of();
        }
        String html = new String(resource.content(), StandardCharsets.UTF_8);
        List<String> imageUrls = extractImageUrls(candidateImageUrl, html);
        List<CandidateImage> images = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            try {
                DownloadedResource imageResource = readFromUrl(imageUrl, MAX_IMAGE_DOWNLOAD_BYTES);
                if (imageResource.content().length > 0 && isImageResource(imageUrl, imageResource)) {
                    images.add(new CandidateImage(imageUrl, imageResource.content()));
                }
            } catch (Exception ex) {
                log.debug("网页候选图片下载失败 pageUrl={} imageUrl={} error={}",
                        candidateImageUrl, imageUrl, ex.getMessage());
            }
            if (images.size() >= MAX_PAGE_IMAGE_CANDIDATES) {
                break;
            }
        }
        return images;
    }

    private DownloadedResource readFromUrl(String url, int maxBytes) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,text/html;q=0.8,*/*;q=0.5");
        try (InputStream inputStream = connection.getInputStream()) {
            int contentLength = connection.getContentLength();
            int limit = isHtmlContentType(connection.getContentType())
                    ? Math.min(maxBytes, MAX_PAGE_DOWNLOAD_BYTES)
                    : maxBytes;
            if (contentLength > limit) {
                return new DownloadedResource(connection.getContentType(), new byte[0]);
            }
            return new DownloadedResource(connection.getContentType(), readAtMost(inputStream, limit));
        } finally {
            connection.disconnect();
        }
    }

    private byte[] readAtMost(InputStream inputStream, int maxBytes) throws IOException {
        byte[] buffer = inputStream.readNBytes(maxBytes + 1);
        if (buffer.length > maxBytes) {
            return new byte[0];
        }
        return buffer;
    }

    private boolean isImageResource(String url, DownloadedResource resource) {
        String contentType = resource.contentType();
        if (StringUtils.hasText(contentType) && contentType.toLowerCase().startsWith("image/")) {
            return !contentType.toLowerCase().contains("svg");
        }
        String lowerUrl = url.toLowerCase();
        return lowerUrl.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp)(?:[?#].*)?$");
    }

    private boolean isHtmlResource(String url, DownloadedResource resource) {
        if (isHtmlContentType(resource.contentType())) {
            return true;
        }
        String lowerUrl = url.toLowerCase();
        return !lowerUrl.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|svg|pdf|zip)(?:[?#].*)?$");
    }

    private boolean isHtmlContentType(String contentType) {
        return StringUtils.hasText(contentType) && contentType.toLowerCase().contains("text/html");
    }

    private List<String> extractImageUrls(String pageUrl, String html) {
        Set<String> imageUrls = new LinkedHashSet<>();
        collectImageUrls(imageUrls, pageUrl, html, META_IMAGE_PATTERN);
        collectImageUrls(imageUrls, pageUrl, html, META_IMAGE_REVERSED_PATTERN);
        collectImageUrls(imageUrls, pageUrl, html, IMG_SRC_PATTERN);
        return imageUrls.stream()
                .filter(this::isLikelyPersonImageCandidate)
                .limit(MAX_PAGE_IMAGE_CANDIDATES)
                .toList();
    }

    private void collectImageUrls(Set<String> imageUrls, String pageUrl, String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find() && imageUrls.size() < MAX_PAGE_IMAGE_CANDIDATES) {
            String resolved = resolveUrl(pageUrl, matcher.group(1));
            if (StringUtils.hasText(resolved)) {
                imageUrls.add(resolved);
            }
        }
    }

    private String resolveUrl(String pageUrl, String rawImageUrl) {
        if (!StringUtils.hasText(rawImageUrl) || rawImageUrl.startsWith("data:")) {
            return null;
        }
        try {
            URI resolved = URI.create(pageUrl).resolve(rawImageUrl.trim());
            String scheme = resolved.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    ? resolved.toString()
                    : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isLikelyPersonImageCandidate(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        return !lower.contains("favicon")
                && !lower.contains("sprite")
                && !lower.contains("logo")
                && !lower.matches(".*\\.(svg|ico)(?:[?#].*)?$");
    }

    private String resolveContentType(MultipartFile originalImage) {
        return StringUtils.hasText(originalImage.getContentType()) ? originalImage.getContentType() : "image/jpeg";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean isComprefaceEnabled() {
        return properties == null
                || properties.getApi() == null
                || properties.getApi().getCompreface() == null
                || properties.getApi().getCompreface().isEnabled();
    }

    private record DownloadedResource(String contentType, byte[] content) {
    }

    private record CandidateImage(String url, byte[] content) {
    }
}

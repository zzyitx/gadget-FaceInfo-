package com.example.face2info.service.impl;

import com.example.face2info.service.ImageSimilarityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

@Slf4j
@Service
public class ImageSimilarityServiceImpl implements ImageSimilarityService {

    private static final int HASH_SIZE = 8;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;

    @Override
    public double score(MultipartFile originalImage, String candidateImageUrl, double fallbackScore) {
        if (!StringUtils.hasText(candidateImageUrl) || originalImage == null || originalImage.isEmpty()) {
            return fallbackScore;
        }
        try {
            BufferedImage origin = ImageIO.read(new ByteArrayInputStream(originalImage.getBytes()));
            BufferedImage candidate = readFromUrl(candidateImageUrl);
            if (origin == null || candidate == null) {
                return fallbackScore;
            }
            long hashA = averageHash(origin);
            long hashB = averageHash(candidate);
            int distance = Long.bitCount(hashA ^ hashB);
            double score = (1.0 - (distance / 64.0)) * 100.0;
            return round(score);
        } catch (Exception ex) {
            log.debug("图像相似度计算失败，使用回退分 url={} error={}", candidateImageUrl, ex.getMessage());
            return fallbackScore;
        }
    }

    private BufferedImage readFromUrl(String candidateImageUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(candidateImageUrl).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream inputStream = connection.getInputStream()) {
            return ImageIO.read(inputStream);
        } finally {
            connection.disconnect();
        }
    }

    private long averageHash(BufferedImage source) {
        BufferedImage resized = new BufferedImage(HASH_SIZE, HASH_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, HASH_SIZE, HASH_SIZE, null);
        } finally {
            graphics.dispose();
        }

        int[] pixels = new int[HASH_SIZE * HASH_SIZE];
        resized.getRaster().getPixels(0, 0, HASH_SIZE, HASH_SIZE, pixels);
        double average = 0.0;
        for (int pixel : pixels) {
            average += pixel;
        }
        average /= pixels.length;

        long hash = 0L;
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] >= average) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

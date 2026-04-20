package com.example.face2info.service.impl;

import com.example.face2info.client.CompreFaceVerificationClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageSimilarityServiceImplTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldUseVerificationSimilarityAsPercentScore() throws Exception {
        CompreFaceVerificationClient verificationClient = mock(CompreFaceVerificationClient.class);
        when(verificationClient.verify(any(), any(), anyString())).thenReturn(OptionalDouble.of(0.9732D));
        ImageSimilarityServiceImpl service = new ImageSimilarityServiceImpl(verificationClient);

        String candidateUrl = startImageServer(createPngBytes());
        double score = service.score(createImage(), candidateUrl, 12.0D);

        assertThat(score).isEqualTo(97.32D);
    }

    @Test
    void shouldFallbackWhenVerificationReturnsEmpty() throws Exception {
        CompreFaceVerificationClient verificationClient = mock(CompreFaceVerificationClient.class);
        when(verificationClient.verify(any(), any(), anyString())).thenReturn(OptionalDouble.empty());
        ImageSimilarityServiceImpl service = new ImageSimilarityServiceImpl(verificationClient);

        String candidateUrl = startImageServer(createPngBytes());
        double score = service.score(createImage(), candidateUrl, 12.0D);

        assertThat(score).isEqualTo(12.0D);
    }

    private MockMultipartFile createImage() throws IOException {
        return new MockMultipartFile("image", "origin.png", "image/png", createPngBytes());
    }

    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private String startImageServer(byte[] payload) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/candidate.png", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/candidate.png";
    }
}

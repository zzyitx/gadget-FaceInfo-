package com.example.face2info.service.impl;

import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceRecognitionServiceImplTest {

    private static final String PREVIEW_URL = "https://tempfile.org/kN8mP2xQvR7/preview";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SerpApiClient serpApiClient = mock(SerpApiClient.class);
    private final TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
    private final NameExtractor nameExtractor = new NameExtractor();

    @Test
    void shouldUseBingAndYandexSourcesWhenCollectingEvidence() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": { "title": "Jay Chou" },
                          "visual_matches": [{
                            "position": 1,
                            "title": "Jay Chou profile",
                            "link": "https://example.com/a",
                            "source": "Lens"
                          }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_results": [{ "title": "Jay Chou singer", "link": "https://example.com/b", "source": "Yandex" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_results": [{ "title": "Jay Chou concert", "link": "https://example.com/c", "source": "Yandex" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_results": [{ "title": "Jay Chou live", "link": "https://example.com/d", "source": "Bing" }]
                        }
                        """)));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getImageMatches()).hasSize(1);
        assertThat(result.getSeedQueries()).contains("Jay Chou");
        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl)
                .contains("https://example.com/a", "https://example.com/b", "https://example.com/c", "https://example.com/d");
    }

    @Test
    void shouldDeduplicateEvidenceUrlsAcrossSources() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": { "title": "Jay Chou" },
                          "visual_matches": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_results": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Yandex" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getWebEvidences()).hasSize(1);
        assertThat(result.getSeedQueries()).contains("Jay Chou");
    }

    @Test
    void shouldLimitVisualMatchesToTwenty() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree(buildVisualMatchesPayload(25))));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        assertThat(service.recognize(image).getImageMatches()).hasSize(20);
    }

    @Test
    void shouldUploadImageThroughTmpfilesClientInterface() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        { "knowledge_graph": { "title": "Lei Jun" } }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        assertThat(service.recognize(image).getSeedQueries()).contains("Lei Jun");
        verify(tmpfilesClient).uploadImage(image);
        verify(serpApiClient).reverseImageSearchByUrl(PREVIEW_URL);
    }

    @Test
    void shouldContinueWhenYandexFailsButLensProvidesEvidence() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": { "title": "Jay Chou" },
                          "visual_matches": [{ "title": "Jay Chou profile", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenThrow(new RuntimeException("timeout"));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenThrow(new RuntimeException("timeout"));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl).contains("https://example.com/a");
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("yandex_images_about"));
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("yandex_images_similar"));
    }

    @Test
    void shouldUseUploadedImageUrlForBingSearch() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        { "knowledge_graph": { "title": "Lei Jun" } }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        service.recognize(image);

        verify(serpApiClient).reverseImageSearchByUrlBing(PREVIEW_URL);
        verify(serpApiClient, never()).searchBingImages(anyString());
    }

    @Test
    void shouldContinueWhenBingImageUrlSearchFails() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": { "title": "Jay Chou" },
                          "visual_matches": [{ "title": "Jay Chou profile", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenThrow(new RuntimeException("timeout"));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("bing_images: timeout"));
        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl).contains("https://example.com/a");
    }

    private String buildVisualMatchesPayload(int count) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"knowledge_graph\":{\"title\":\"Lei Jun\"},\"visual_matches\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"position\":").append(i).append(",")
                    .append("\"title\":\"Match ").append(i).append("\",")
                    .append("\"link\":\"https://example.com/").append(i).append("\",")
                    .append("\"source\":\"Example\"")
                    .append("}");
        }
        builder.append("]}");
        return builder.toString();
    }
}

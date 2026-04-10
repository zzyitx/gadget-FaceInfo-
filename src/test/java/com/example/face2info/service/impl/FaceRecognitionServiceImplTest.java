package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.SummaryGenerationClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceRecognitionServiceImplTest {

    private static final String PREVIEW_URL = "https://tempfile.org/kN8mP2xQvR7/preview";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
    private final SerpApiClient serpApiClient = mock(SerpApiClient.class);
    private final TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
    private final SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);
    private final NameExtractor nameExtractor = new NameExtractor();

    @Test
    void shouldUseBingAndYandexSourcesWhenCollectingEvidence() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Jay Chou"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Jay Chou" },
                          "organic": [{
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

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getImageMatches()).hasSize(1);
        assertThat(result.getImageMatches().get(0).getThumbnailUrl()).isNull();
        assertThat(result.getSeedQueries()).contains("Jay Chou");
        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl)
                .contains("https://example.com/a", "https://example.com/b", "https://example.com/c", "https://example.com/d");
    }

    @Test
    void shouldDeduplicateEvidenceUrlsAcrossSources() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Jay Chou"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Jay Chou" },
                          "organic": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_results": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Yandex" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getWebEvidences()).hasSize(1);
        assertThat(result.getSeedQueries()).contains("Jay Chou");
    }

    @Test
    void shouldLimitVisualMatchesToTenAndSortBySimilarityScore() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Lei Jun"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree(buildOrganicPayload(25))));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence evidence = service.recognize(image);

        assertThat(evidence.getImageMatches()).hasSize(10);
        assertThat(evidence.getImageMatches())
                .extracting(match -> match.getSimilarityScore())
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(evidence.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/1.jpg");
        assertThat(evidence.getImageMatches().get(0).getSimilarityScore()).isGreaterThan(evidence.getImageMatches().get(9).getSimilarityScore());
    }

    @Test
    void shouldUseSerperThumbnailAndHeuristicScoreForImageMatches() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Lei Jun"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Lei Jun" },
                          "organic": [
                            {
                              "title": "Lei Jun official profile",
                              "link": "https://example.com/official",
                              "source": "Wikipedia",
                              "thumbnailUrl": "https://thumb.example.com/official.jpg"
                            },
                            {
                              "title": "Technology leader article",
                              "link": "https://example.com/article",
                              "source": "Tech Blog",
                              "thumbnailUrl": "https://thumb.example.com/article.jpg"
                            }
                          ]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence evidence = service.recognize(image);

        assertThat(evidence.getImageMatches()).hasSize(2);
        assertThat(evidence.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/official.jpg");
        assertThat(evidence.getImageMatches().get(0).getLink()).isEqualTo("https://example.com/official");
        assertThat(evidence.getImageMatches().get(0).getSimilarityScore()).isGreaterThan(90.0);
        assertThat(evidence.getImageMatches().get(1).getSimilarityScore()).isLessThan(evidence.getImageMatches().get(0).getSimilarityScore());
    }

    @Test
    void shouldUploadImageThroughTmpfilesClientInterface() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Lei Jun"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        { "knowledgeGraph": { "title": "Lei Jun" } }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        assertThat(service.recognize(image).getSeedQueries()).contains("Lei Jun");
        verify(tmpfilesClient).uploadImage(image);
        verify(googleSearchClient).reverseImageSearchByUrl(PREVIEW_URL);
        verify(summaryGenerationClient).recognizeFaceCandidateNames(image);
    }

    @Test
    void shouldContinueWhenYandexFailsButLensProvidesEvidence() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Jay Chou"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Jay Chou" },
                          "organic": [{ "title": "Jay Chou profile", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenThrow(new RuntimeException("timeout"));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenThrow(new RuntimeException("timeout"));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl).contains("https://example.com/a");
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("yandex_images_about"));
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("yandex_images_similar"));
    }

    @Test
    void shouldUseUploadedImageUrlForBingSearch() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Lei Jun"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        { "knowledgeGraph": { "title": "Lei Jun" } }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        service.recognize(image);

        verify(serpApiClient).reverseImageSearchByUrlBing(PREVIEW_URL);
        verify(serpApiClient, never()).searchBingImages(anyString());
    }

    @Test
    void shouldContinueWhenBingImageUrlSearchFails() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Jay Chou"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Jay Chou" },
                          "organic": [{ "title": "Jay Chou profile", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenThrow(new RuntimeException("timeout"));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("bing_images: timeout"));
        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl).contains("https://example.com/a");
    }

    @Test
    void shouldUseInputImageWithoutSecondEnhancement() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(summaryGenerationClient.recognizeFaceCandidateNames(image)).thenReturn(List.of("Lei Jun"));
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"organic\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{" + "\"image_results\": []}")));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, summaryGenerationClient);

        RecognitionEvidence evidence = service.recognize(image);

        verify(tmpfilesClient).uploadImage(image);
        verify(summaryGenerationClient).recognizeFaceCandidateNames(image);
        assertThat(evidence.getSeedQueries()).containsExactly("Lei Jun");
    }

    private String buildOrganicPayload(int count) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"knowledgeGraph\":{\"title\":\"Lei Jun\"},\"organic\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(",");
            }
            builder.append("{")
                    .append("\"title\":\"Match ").append(i).append("\",")
                    .append("\"link\":\"https://example.com/").append(i).append("\",")
                    .append("\"source\":\"Example\",")
                    .append("\"thumbnailUrl\":\"https://thumb.example.com/").append(i).append(".jpg\"")
                    .append("}");
        }
        builder.append("]}");
        return builder.toString();
    }
}


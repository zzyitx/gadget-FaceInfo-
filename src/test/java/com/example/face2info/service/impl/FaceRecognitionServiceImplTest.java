package com.example.face2info.service.impl;

import com.example.face2info.client.GoogleSearchClient;
import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.entity.internal.WebEvidence;
import com.example.face2info.service.ImageResultCacheService;
import com.example.face2info.service.ImageSimilarityService;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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
    private final ImageSimilarityService imageSimilarityService = mock(ImageSimilarityService.class);
    private final ImageResultCacheService imageResultCacheService = mock(ImageResultCacheService.class);
    private final NameExtractor nameExtractor = new NameExtractor();

    @Test
    void shouldUsePreparedUploadedImageUrlWithoutUploadingAgain() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Jay Chou" },
                          "organic": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Lens" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        service.recognize(image, PREVIEW_URL);

        verify(tmpfilesClient, never()).uploadImage(image);
        verify(googleSearchClient).reverseImageSearchByUrl(PREVIEW_URL);
    }

    @Test
    void shouldUseBingAndYandexSourcesWhenCollectingEvidenceAndSeedQueriesFromSearchResults() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Jay Chou" },
                          "organic": [{
                            "title": "Jay Chou profile",
                            "link": "https://example.com/a",
                            "source": "Lens",
                            "thumbnailUrl": "https://example.com/a.jpg"
                          }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_preview": { "image": { "link": "https://example.com/about-preview.jpg" } },
                          "image_results": [{ "title": "Jay Chou singer", "link": "https://example.com/b", "source": "Yandex", "thumbnail": "https://example.com/b.jpg" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "image_results": [{ "title": "Jay Chou concert", "link": "https://example.com/c", "source": "Yandex", "thumbnail": "https://example.com/c.jpg" }]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "related_content": [{ "title": "Jay Chou live", "link": "https://example.com/d", "source": "Bing", "thumbnail": "https://example.com/d.jpg" }]
                        }
                        """)));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getImageMatches()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.getImageMatches()).extracting("thumbnailUrl")
                .contains("https://example.com/a.jpg", "https://example.com/b.jpg", "https://example.com/d.jpg");
        assertThat(result.getSeedQueries()).contains("Jay Chou");
        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl)
                .contains("https://example.com/a", "https://example.com/b", "https://example.com/c", "https://example.com/d");
    }

    @Test
    void shouldDeduplicateEvidenceUrlsAcrossSources() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
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
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getWebEvidences()).hasSize(1);
        assertThat(result.getSeedQueries()).contains("Jay Chou");
    }

    @Test
    void shouldLimitVisualMatchesToTenAndSortBySimilarityScore() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree(buildOrganicPayload(25))));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence evidence = service.recognize(image);

        assertThat(evidence.getImageMatches()).hasSize(10);
        assertThat(evidence.getImageMatches())
                .extracting(match -> match.getSimilarityScore())
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(evidence.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/1.jpg");
        assertThat(evidence.getImageMatches().get(0).getSimilarityScore()).isGreaterThan(evidence.getImageMatches().get(9).getSimilarityScore());
    }

    @Test
    void shouldContinueWhenYandexFailsButLensProvidesEvidence() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
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
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence result = service.recognize(image);

        assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl).contains("https://example.com/a");
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("yandex_images_about"));
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error).contains("yandex_images_similar"));
    }

    @Test
    void shouldExcludeTemporaryHostCandidatesFromImageMatches() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledgeGraph": { "title": "Lei Jun" },
                          "organic": [
                            {
                              "title": "uploaded temp image",
                              "link": "https://tempfile.org/kN8mP2xQvR7/preview",
                              "source": "Temp",
                              "thumbnailUrl": "https://tempfile.org/kN8mP2xQvR7/preview"
                            },
                            {
                              "title": "valid profile",
                              "link": "https://example.com/profile",
                              "source": "Wikipedia",
                              "thumbnailUrl": "https://thumb.example.com/profile.jpg"
                            }
                          ]
                        }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence evidence = service.recognize(image);

        assertThat(evidence.getImageMatches()).hasSize(1);
        assertThat(evidence.getImageMatches().get(0).getLink()).isEqualTo("https://example.com/profile");
        assertThat(evidence.getImageMatches().get(0).getThumbnailUrl()).isEqualTo("https://thumb.example.com/profile.jpg");
    }

    @Test
    void shouldUseUploadedImageUrlForBingSearch() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        { "knowledgeGraph": { "title": "Lei Jun" } }
                        """)));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        service.recognize(image);

        verify(serpApiClient).reverseImageSearchByUrlBing(PREVIEW_URL);
        verify(serpApiClient, never()).searchBingImages(anyString());
    }

    @Test
    void shouldReturnRecognitionEvidenceFromCacheBeforeCallingProviders() {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence cached = new RecognitionEvidence().setSeedQueries(List.of("Cached Person"));
        when(imageResultCacheService.getRecognitionEvidence(image)).thenReturn(cached);

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence result = service.recognize(image);

        assertThat(result).isSameAs(cached);
        verify(tmpfilesClient, never()).uploadImage(image);
        verify(googleSearchClient, never()).reverseImageSearchByUrl(anyString());
        verify(serpApiClient, never()).reverseImageSearchByUrlBing(anyString());
    }

    @Test
    void shouldCacheRecognitionEvidenceAfterProviderCalls() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(imageResultCacheService.getRecognitionEvidence(image)).thenReturn(null);
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"knowledgeGraph\":{\"title\":\"Lei Jun\"},\"organic\":[{\"title\":\"Lei Jun\",\"link\":\"https://example.com/a\",\"source\":\"Lens\"}]}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence result = service.recognize(image);

        verify(imageResultCacheService).cacheRecognitionEvidence(image, result);
    }

    @Test
    void shouldQueryReverseImageProvidersConcurrently() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();

        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenAnswer(invocation -> delayedEmptyResponse(activeCalls, maxActiveCalls));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenAnswer(invocation -> delayedEmptyResponse(activeCalls, maxActiveCalls));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenAnswer(invocation -> delayedEmptyResponse(activeCalls, maxActiveCalls));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenAnswer(invocation -> delayedEmptyResponse(activeCalls, maxActiveCalls));

        FaceRecognitionServiceImpl service = createService();

        service.recognize(image);

        assertThat(maxActiveCalls.get()).isGreaterThan(1);
    }

    @Test
    void shouldUseInputImageWithoutSecondEnhancement() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"organic\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));
        when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("{\"image_results\": []}")));

        FaceRecognitionServiceImpl service = createService();

        RecognitionEvidence evidence = service.recognize(image);

        verify(tmpfilesClient).uploadImage(image);
        assertThat(evidence.getSeedQueries()).isEmpty();
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

    private SerpApiResponse delayedEmptyResponse(AtomicInteger activeCalls, AtomicInteger maxActiveCalls) {
        int current = activeCalls.incrementAndGet();
        maxActiveCalls.accumulateAndGet(current, Math::max);
        try {
            Thread.sleep(150L);
            return new SerpApiResponse().setRoot(objectMapper.readTree("{\"image_results\": []}"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            activeCalls.decrementAndGet();
        }
    }

    private FaceRecognitionServiceImpl createService() {
        when(imageSimilarityService.score(any(), anyString(), anyDouble()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.initialize();
        return new FaceRecognitionServiceImpl(
                googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient, imageSimilarityService, imageResultCacheService, executor);
    }
}

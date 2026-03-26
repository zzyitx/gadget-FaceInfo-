package com.example.face2info.service.impl;

import com.example.face2info.client.SerpApiClient;
import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.entity.internal.SerpApiResponse;
import com.example.face2info.exception.FaceRecognitionException;
import com.example.face2info.util.NameExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaceRecognitionServiceImplTest {

    private static final String PREVIEW_URL = "https://tempfile.org/kN8mP2xQvR7/preview";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SerpApiClient serpApiClient = mock(SerpApiClient.class);
    private final TmpfilesClient tmpfilesClient = mock(TmpfilesClient.class);
    private final NameExtractor nameExtractor = new NameExtractor();

    @Test
    void shouldPreferKnowledgeGraphTitle() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        {
                          "knowledge_graph": { "title": "周杰伦" },
                          "visual_matches": [{
                            "position": 1,
                            "title": "Lei Jun",
                            "link": "https://example.com/profile",
                            "source": "Example",
                            "image": "https://example.com/lei.jpg"
                          }],
                          "image_results": [{ "title": "Jay Chou 高清图片" }]
                        }
                        """)));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        var result = service.recognize(image);
        assertThat(result.getName()).isEqualTo("周杰伦");
        assertThat(result.getImageMatches()).hasSize(1);
        assertThat(result.getImageMatches().get(0).getPosition()).isEqualTo(1);
        assertThat(result.getImageMatches().get(0).getLink()).isEqualTo("https://example.com/profile");
    }

    @Test
    void shouldFailWhenCandidateConfidenceIsTooLow() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree("""
                        { "image_results": [{ "title": "高清壁纸 photo image" }] }
                        """)));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        var result = service.recognize(image);
        assertThat(result.getName()).isNull();
        assertThat(result.getError()).isEqualTo("Unable to recognize the person from the image.");
    }

    @Test
    void shouldLimitVisualMatchesToTwenty() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
        when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
                .setRoot(objectMapper.readTree(buildVisualMatchesPayload(25))));

        FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

        assertThat(service.recognize(image).getImageMatches()).hasSize(20);
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

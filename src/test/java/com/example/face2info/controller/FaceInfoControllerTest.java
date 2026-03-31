package com.example.face2info.controller;

import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.entity.response.FaceCheckMatch;
import com.example.face2info.entity.response.PersonInfo;
import com.example.face2info.exception.GlobalExceptionHandler;
import com.example.face2info.service.Face2InfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaceInfoController.class)
@Import(GlobalExceptionHandler.class)
class FaceInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Face2InfoService face2InfoService;

    @Test
    void shouldReturnSuccessfulPayload() throws Exception {
        when(face2InfoService.process(any())).thenReturn(new FaceInfoResponse().setStatus("success"));

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void shouldExposeFaceCheckMatchesInResponseJson() throws Exception {
        when(face2InfoService.process(any())).thenReturn(new FaceInfoResponse()
                .setStatus("success")
                .setFacecheckMatches(java.util.List.of(
                        new FaceCheckMatch()
                                .setImageDataUrl("data:image/jpeg;base64,AAA")
                                .setSimilarityScore(97.2)
                                .setSourceHost("instagram.com")
                                .setSourceUrl("https://instagram.com/p/demo")
                                .setGroup(1)
                                .setSeen(3)
                                .setIndex(0)
                )));

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facecheck_matches[0].image_data_url").value("data:image/jpeg;base64,AAA"))
                .andExpect(jsonPath("$.facecheck_matches[0].similarity_score").value(97.2))
                .andExpect(jsonPath("$.facecheck_matches[0].source_host").value("instagram.com"));
    }

    @Test
    void shouldExposeSummaryTagsAndWarningsInResponseJson() throws Exception {
        when(face2InfoService.process(any())).thenReturn(new FaceInfoResponse()
                .setStatus("partial")
                .setWarnings(java.util.List.of("正文智能处理暂时不可用"))
                .setPerson(new PersonInfo()
                        .setName("周杰伦")
                        .setSummary("周杰伦是华语流行乐代表人物。")
                        .setTags(java.util.List.of("歌手", "音乐制作人"))));

        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/face2info").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("partial"))
                .andExpect(jsonPath("$.person.summary").value("周杰伦是华语流行乐代表人物。"))
                .andExpect(jsonPath("$.person.tags[0]").value("歌手"))
                .andExpect(jsonPath("$.warnings[0]").value("正文智能处理暂时不可用"));
    }

    @Test
    void shouldNotCommitFallbackSecretsInApplicationGitConfig() throws Exception {
        Path path = Path.of("src/main/resources/application-git.yml");

        assertThat(path).exists();
        assertThat(Files.readAllLines(path))
                .filteredOn(line -> line.contains("api-key:"))
                .allMatch(line -> line.matches(".*\\$\\{[A-Z0-9_]+:}\\s*$"),
                        "Git version config must not contain committed fallback secrets");
    }

    @Test
    void shouldContainPrimaryApiSectionsInApplicationGitConfig() throws Exception {
        String content = Files.readString(Path.of("src/main/resources/application-git.yml"));

        assertThat(content).contains("serp:");
        assertThat(content).contains("news:");
        assertThat(content).contains("jina:");
        assertThat(content).contains("kimi:");
        assertThat(content).contains("summary:");
    }

    @Test
    void shouldContainFacecheckSectionInApplicationGitConfig() throws Exception {
        String content = Files.readString(Path.of("src/main/resources/application-git.yml"));

        assertThat(content).contains("facecheck:");
        assertThat(content).contains("upload-path:");
        assertThat(content).contains("reset-prev-images:");
    }

    @Test
    void shouldTrackApplicationGitConfigAndIgnoreLocalApplicationConfig() throws Exception {
        String ignoreContent = Files.readString(Path.of(".gitignore"));

        assertThat(ignoreContent).contains("src/main/resources/application.yml");
        assertThat(ignoreContent).contains("!src/main/resources/application-git.yml");
    }

    @Test
    void shouldMentionFacecheckConfigInReadme() throws Exception {
        String content = Files.readString(Path.of("README.md"));

        assertThat(content).contains("FaceCheck");
        assertThat(content).contains("FACECHECK_API_KEY");
    }
}

package com.example.face2info.controller;

import com.example.face2info.entity.response.FaceInfoResponse;
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
    void shouldNotCommitFallbackSecretsInApplicationConfig() throws Exception {
        assertThat(Files.readAllLines(Path.of("src/main/resources/application.yml")))
                .filteredOn(line -> line.contains("api-key:"))
                .allMatch(line -> line.matches(".*\\$\\{[A-Z0-9_]+:}\\s*$"),
                        "API key placeholders must not contain committed fallback secrets");
    }
}

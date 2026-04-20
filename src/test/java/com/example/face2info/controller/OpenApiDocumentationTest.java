package com.example.face2info.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeFaceInfoApiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Face2Info API"))
                .andExpect(jsonPath("$.paths['/api/face2info'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/face2info/detect'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.components.schemas.FaceInfoResponse.properties.news").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PersonInfo.properties.name.description")
                        .value("识别或聚合后得到的人物姓名"))
                .andExpect(jsonPath("$.components.schemas.PersonInfo.properties.description").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.timestamp.description")
                        .value("错误发生时间，使用 ISO-8601 时间格式"));
    }
}

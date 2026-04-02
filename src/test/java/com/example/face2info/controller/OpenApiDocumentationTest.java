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
                .andExpect(jsonPath("$.paths['/api/face2info'].post.summary").value("上传人脸图片并聚合人物公开信息"))
                .andExpect(jsonPath("$.paths['/api/face2info'].post.description")
                        .value("接收前端上传的人脸图片，执行候选人物识别、公开资料抓取与结果聚合，返回统一结构的响应数据。"))
                .andExpect(jsonPath("$.paths['/api/face2info'].post.requestBody.content['multipart/form-data'].schema.properties.image.description")
                        .value("图片文件，仅支持常见图片格式的人脸照片"))
                .andExpect(jsonPath("$.components.schemas.FaceInfoResponse.properties.news.description")
                        .value("候选人物相关的新闻列表"))
                .andExpect(jsonPath("$.components.schemas.PersonInfo.properties.name.description")
                        .value("识别或聚合后得到的人物姓名"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.timestamp.description")
                        .value("错误发生时间，使用 ISO-8601 时间格式"));
    }
}

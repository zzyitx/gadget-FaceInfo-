package com.example.face2info.controller;

import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.exception.GlobalExceptionHandler;
import com.example.face2info.service.Face2InfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FaceInfoController.class)
@Import(GlobalExceptionHandler.class)
/**
 * 控制器层测试。
 */
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
}

package com.example.face2info.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaticPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderFacecheckMatchSectionOnIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"matchesCard\"")))
                .andExpect(content().string(containsString("facecheck_matches")))
                .andExpect(content().string(containsString("renderFacecheckMatches")))
                .andExpect(content().string(containsString("FaceCheck")))
                .andExpect(content().string(containsString("source_url")))
                .andExpect(content().string(containsString("similarity_score")))
                .andExpect(content().string(containsString("id=\"rawCard\"")))
                .andExpect(content().string(containsString("<details")));
    }
}

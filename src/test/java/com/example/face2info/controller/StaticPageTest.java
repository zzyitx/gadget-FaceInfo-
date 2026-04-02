package com.example.face2info.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
                .andExpect(content().string(containsString("source_url")))
                .andExpect(content().string(containsString("similarity_score")))
                .andExpect(content().string(containsString("id=\"debugPanel\"")))
                .andExpect(content().string(containsString("<details")));
    }

    @Test
    void shouldRenderCollapsedNewsSectionWithArticleSourceLink() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"newsCard\"")))
                .andExpect(content().string(containsString("news-card")))
                .andExpect(content().string(not(containsString("id=\"newsCard\" open"))))
                .andExpect(content().string(containsString("renderNewsCard")))
                .andExpect(content().string(containsString("renderArticleGroups")))
                .andExpect(content().string(containsString("image_matches")))
                .andExpect(content().string(containsString("item.link")))
                .andExpect(content().string(containsString("news-toggle")))
                .andExpect(content().string(containsString("news-source-link")))
                .andExpect(content().string(containsString("item.url")));
    }

    @Test
    void shouldRenderIndependentUploadPanelAndCollapsedInfoPanels() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"uploadPanel\"")))
                .andExpect(content().string(containsString("main-layout")))
                .andExpect(content().string(containsString("id=\"socialCard\"")))
                .andExpect(content().string(containsString("id=\"debugPanel\"")))
                .andExpect(content().string(not(containsString("id=\"socialCard\" open"))))
                .andExpect(content().string(not(containsString("id=\"debugPanel\" open"))));
    }
}

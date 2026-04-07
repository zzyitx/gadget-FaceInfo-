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
    void shouldRenderSerperImageMatchSectionOnIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"matchesCard\"")))
                .andExpect(content().string(not(containsString("facecheck_matches"))))
                .andExpect(content().string(containsString("renderImageMatches")))
                .andExpect(content().string(containsString("thumbnail_url")))
                .andExpect(content().string(containsString("similarity_score")))
                .andExpect(content().string(containsString("image-item")))
                .andExpect(content().string(containsString("window.open")))
                .andExpect(content().string(containsString("id=\"debugCard\"")))
                .andExpect(content().string(containsString("compactJson(data)")));
    }

    @Test
    void shouldRenderSelectionRequiredFlowOnIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("selection_required")))
                .andExpect(content().string(containsString("id=\"selectionCard\"")))
                .andExpect(content().string(containsString("id=\"selectionFaceGrid\"")))
                .andExpect(content().string(containsString("id=\"selectionPreview\"")))
                .andExpect(content().string(containsString("requestJson(\"/api/face2info/process-selected\"")))
                .andExpect(content().string(containsString("data-face-id")))
                .andExpect(content().string(containsString("markSelected")));
    }

    @Test
    void shouldRenderDetectFirstUploadAndResultPanels() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("detect-first")))
                .andExpect(content().string(containsString("requestMultipart(\"/api/face2info\"")))
                .andExpect(content().string(containsString("id=\"faceForm\"")))
                .andExpect(content().string(containsString("id=\"socialCard\"")))
                .andExpect(content().string(containsString("id=\"newsCard\"")))
                .andExpect(content().string(containsString("id=\"debugCard\"")))
                .andExpect(content().string(containsString("id=\"personCard\"")));
    }

    @Test
    void shouldRenderImageMatchesAndSelectionCardsWithCoverLayout() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".image-frame")))
                .andExpect(content().string(containsString(".face-card img")))
                .andExpect(content().string(containsString("aspect-ratio: 1 / 1")))
                .andExpect(content().string(containsString("object-fit: cover")))
                .andExpect(content().string(containsString("class=\"image-frame\"")))
                .andExpect(content().string(containsString(".preview img")))
                .andExpect(content().string(containsString("selection-empty")));
    }

    @Test
    void shouldRenderImageDrivenNewsAndSelectionStateMessages() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"statusBox\""))))
                .andExpect(content().string(not(containsString("class=\"status\""))))
                .andExpect(content().string(containsString("renderNews(Array.isArray(data.news) ? data.news : [], Array.isArray(data.image_matches) ? data.image_matches : [])")))
                .andExpect(content().string(containsString("selectionProgress")))
                .andExpect(content().string(containsString("selection_required")))
                .andExpect(content().string(containsString("match-group-title")));
    }

    @Test
    void shouldRenderFaceCardMetadataAndBBoxText() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("face-card-title")))
                .andExpect(content().string(containsString("face-card-meta")))
                .andExpect(content().string(containsString("face-card-bbox")))
                .andExpect(content().string(containsString("bboxText(face.bbox)")))
                .andExpect(content().string(containsString("formatPercent(face.confidence)")));
    }
}

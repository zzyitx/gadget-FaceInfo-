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
    void shouldRenderUploadAndSelectionFlowOnIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"faceForm\"")))
                .andExpect(content().string(containsString("id=\"imageInput\"")))
                .andExpect(content().string(containsString("class=\"upload-actions\"")))
                .andExpect(content().string(containsString("id=\"uploadActionSlot\"")))
                .andExpect(content().string(containsString("id=\"selectionActionSlot\"")))
                .andExpect(content().string(containsString("id=\"submitButton\"")))
                .andExpect(content().string(containsString("id=\"selectionCard\"")))
                .andExpect(content().string(containsString("id=\"selectionFaceGrid\"")))
                .andExpect(content().string(containsString("requestMultipart(\"/api/face2info\"")))
                .andExpect(content().string(containsString("requestJson(\"/api/face2info/process-selected\"")))
                .andExpect(content().string(containsString("sessionStorage.setItem(SEARCH_RESULT_KEY")))
                .andExpect(content().string(containsString("window.location.href = \"/result.html\"")));
    }

    @Test
    void shouldRenderTopTitleAndStatusAreaOnIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"faceForm\"")))
                .andExpect(content().string(containsString("id=\"submitButton\" type=\"submit\" form=\"faceForm\"")))
                .andExpect(content().string(containsString("id=\"statusCard\"")))
                .andExpect(content().string(containsString("id=\"statusTitle\"")))
                .andExpect(content().string(containsString("setStatus(")))
                .andExpect(content().string(containsString("id=\"selectionPreview\"")))
                .andExpect(content().string(containsString(".upload-actions")))
                .andExpect(content().string(containsString("moveActionButtonToUpload()")))
                .andExpect(content().string(containsString("moveActionButtonToSelection()")))
                .andExpect(content().string(containsString(".preview-visible")))
                .andExpect(content().string(containsString("const imageInput = document.getElementById(\"imageInput\")")));
    }

    @Test
    void shouldNotRenderResultPanelsOnIndexPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"matchesCard\""))))
                .andExpect(content().string(not(containsString("id=\"socialCard\""))))
                .andExpect(content().string(not(containsString("id=\"newsCard\""))))
                .andExpect(content().string(not(containsString("id=\"debugCard\""))))
                .andExpect(content().string(not(containsString("id=\"personCard\""))));
    }

    @Test
    void shouldRenderFaceCardMetadataAndBBoxText() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".face-card img")))
                .andExpect(content().string(containsString("aspect-ratio: 1 / 1")))
                .andExpect(content().string(containsString("object-fit: cover")))
                .andExpect(content().string(containsString("face-card-title")))
                .andExpect(content().string(containsString("face-card-meta")))
                .andExpect(content().string(containsString("face-card-bbox")))
                .andExpect(content().string(containsString("bboxText(face.bbox)")))
                .andExpect(content().string(containsString("formatPercent(face.confidence)")));
    }

    @Test
    void shouldRenderResultPageSectionsAndNavigation() throws Exception {
        mockMvc.perform(get("/result.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"personCard\"")))
                .andExpect(content().string(containsString("id=\"socialCard\"")))
                .andExpect(content().string(not(containsString("id=\"newsCard\""))))
                .andExpect(content().string(containsString("id=\"debugCard\"")))
                .andExpect(content().string(containsString("result-layout")))
                .andExpect(content().string(containsString("main-column")))
                .andExpect(content().string(containsString("match-rail")))
                .andExpect(content().string(not(containsString("id=\"matchesCard\""))))
                .andExpect(content().string(containsString("href=\"/index.html\"")))
                .andExpect(content().string(containsString("bootstrapResult()")))
                .andExpect(content().string(containsString("SEARCH_RESULT_KEY")))
                .andExpect(content().string(not(containsString(">独立展示<"))));
    }

    @Test
    void shouldPreserveStructuredProfileSectionLineBreaksOnResultPage() throws Exception {
        mockMvc.perform(get("/result.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".profile-section-body")))
                .andExpect(content().string(containsString(".profile-section-paragraph")))
                .andExpect(content().string(containsString(".source-footnote")))
                .andExpect(content().string(containsString(".citation-tooltip")))
                .andExpect(content().string(containsString("renderProfileSection(")))
                .andExpect(content().string(containsString("family_member_situation_summary")))
                .andExpect(content().string(containsString("family_member_situation_summary_paragraphs")))
                .andExpect(content().string(containsString("renderParagraphBlock(")))
                .andExpect(content().string(containsString("normalizeParagraphs(")))
                .andExpect(content().string(containsString("item.source_urls")))
                .andExpect(content().string(containsString("item.sourceUrls")))
                .andExpect(content().string(containsString("renderParagraphTextWithInlineCitations(")))
                .andExpect(content().string(containsString("data-tooltip")))
                .andExpect(content().string(not(containsString("buildArticleReferenceIndex("))));
    }

    @Test
    void shouldRenderTagsInsideInfoGridInsteadOfStandaloneTagAreaOnResultPage() throws Exception {
        mockMvc.perform(get("/result.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tags.join(\" / \")")))
                .andExpect(content().string(not(containsString("info.occupations"))))
                .andExpect(content().string(not(containsString("class=\"tags\""))));
    }

    @Test
    void shouldRenderResultPageImageAndNewsRenderingLogic() throws Exception {
        mockMvc.perform(get("/result.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("renderImageMatches")))
                .andExpect(content().string(containsString("splitImageMatches")))
                .andExpect(content().string(not(containsString("renderNews"))))
                .andExpect(content().string(containsString("sortImageMatches")))
                .andExpect(content().string(containsString("const articleMatches = Array.isArray(data.article_image_matches) ? data.article_image_matches : matches;")))
                .andExpect(content().string(containsString("const allSortedMatches = sortImageMatches(articleMatches)")))
                .andExpect(content().string(containsString("renderImageMatches(displayMatches")))
                .andExpect(content().string(containsString("collectArticleSources(person)")))
                .andExpect(content().string(containsString("person.article_sources || person.articleSources")))
                .andExpect(content().string(containsString("article_sources: Array.isArray(data.person.article_sources)")))
                .andExpect(content().string(containsString("renderArticleSources(summarySourceArticles, allSortedMatches)")))
                .andExpect(content().string(containsString("buildCitationArticles")))
                .andExpect(content().string(containsString("thumbnail_url")))
                .andExpect(content().string(containsString("similarity_score")))
                .andExpect(content().string(containsString("aggregated_count")))
                .andExpect(content().string(containsString("aggregated_primary")))
                .andExpect(content().string(containsString("data-link")))
                .andExpect(content().string(containsString("window.open")))
                .andExpect(content().string(containsString("match-order")))
                .andExpect(content().string(containsString("match-score")))
                .andExpect(content().string(containsString("match-group")))
                .andExpect(content().string(containsString("match-group-title")))
                .andExpect(content().string(containsString("match-aggregate-count")))
                .andExpect(content().string(containsString("match-article-list")))
                .andExpect(content().string(not(containsString("independent-news-list"))))
                .andExpect(content().string(not(containsString("buildArticleReferenceIndex"))))
                .andExpect(content().string(containsString("article-line-list")))
                .andExpect(content().string(containsString("article-line-item")))
                .andExpect(content().string(containsString("article-line-top")))
                .andExpect(content().string(containsString("article-line-site")))
                .andExpect(content().string(containsString("article-line-link")))
                .andExpect(content().string(not(containsString("article-grid"))))
                .andExpect(content().string(not(containsString("newsList.map(renderIndependentNews)"))));
    }

    @Test
    void shouldNotFallbackImageCardLinkToThumbnailOrRepeatNoArticleCopy() throws Exception {
        mockMvc.perform(get("/result.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("该图片结果暂时没有相关文章"))))
                .andExpect(content().string(not(containsString("item.link || item.thumbnail_url || \"#\""))))
                .andExpect(content().string(containsString("<details")))
                .andExpect(content().string(containsString("debug-details")))
                .andExpect(content().string(containsString("id=\"rawResponse\"")));
    }
}

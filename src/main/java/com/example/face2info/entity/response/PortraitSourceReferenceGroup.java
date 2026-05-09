package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "网页来源、引用和证据链接分组")
public class PortraitSourceReferenceGroup {

    @JsonProperty("web_sources")
    @Schema(description = "人物画像正文引用的网页来源")
    private List<ArticleSourceBadge> webSources = new ArrayList<>();

    @JsonProperty("evidence_urls")
    @Schema(description = "支撑画像聚合的证据 URL")
    private List<String> evidenceUrls = new ArrayList<>();

    @JsonProperty("image_matches")
    @Schema(description = "反向搜图得到的图片匹配来源")
    private List<ImageMatch> imageMatches = new ArrayList<>();

    @JsonProperty("article_image_matches")
    @Schema(description = "文章来源区展示用的图片匹配来源")
    private List<ImageMatch> articleImageMatches = new ArrayList<>();

    @Schema(description = "聚合过程中的降级或部分失败提示")
    private List<String> warnings = new ArrayList<>();

    public List<ArticleSourceBadge> getWebSources() {
        return webSources;
    }

    public PortraitSourceReferenceGroup setWebSources(List<ArticleSourceBadge> webSources) {
        this.webSources = webSources == null ? new ArrayList<>() : new ArrayList<>(webSources);
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public PortraitSourceReferenceGroup setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls == null ? new ArrayList<>() : new ArrayList<>(evidenceUrls);
        return this;
    }

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public PortraitSourceReferenceGroup setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches == null ? new ArrayList<>() : new ArrayList<>(imageMatches);
        return this;
    }

    public List<ImageMatch> getArticleImageMatches() {
        return articleImageMatches;
    }

    public PortraitSourceReferenceGroup setArticleImageMatches(List<ImageMatch> articleImageMatches) {
        this.articleImageMatches = articleImageMatches == null ? new ArrayList<>() : new ArrayList<>(articleImageMatches);
        return this;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public PortraitSourceReferenceGroup setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        return this;
    }
}

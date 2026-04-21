package com.example.face2info.entity.internal;

import com.example.face2info.entity.response.ImageMatch;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 多信源识别证据。
 */
@Schema(description = "识别阶段的多来源证据集合")
public class RecognitionEvidence {

    @Schema(description = "图像匹配结果列表")
    private List<ImageMatch> imageMatches = new ArrayList<>();

    @Schema(description = "供文章来源区完整展示的原始图像匹配结果列表")
    private List<ImageMatch> articleImageMatches = new ArrayList<>();

    @Schema(description = "网页证据列表")
    private List<WebEvidence> webEvidences = new ArrayList<>();

    @Schema(description = "用于搜索扩展的种子查询")
    private List<String> seedQueries = new ArrayList<>();

    @Schema(description = "识别阶段产生的错误信息")
    private List<String> errors = new ArrayList<>();

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public RecognitionEvidence setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches;
        return this;
    }

    public List<ImageMatch> getArticleImageMatches() {
        return articleImageMatches;
    }

    public RecognitionEvidence setArticleImageMatches(List<ImageMatch> articleImageMatches) {
        this.articleImageMatches = articleImageMatches;
        return this;
    }

    public List<WebEvidence> getWebEvidences() {
        return webEvidences;
    }

    public RecognitionEvidence setWebEvidences(List<WebEvidence> webEvidences) {
        this.webEvidences = webEvidences;
        return this;
    }

    public List<String> getSeedQueries() {
        return seedQueries;
    }

    public RecognitionEvidence setSeedQueries(List<String> seedQueries) {
        this.seedQueries = seedQueries;
        return this;
    }

    public List<String> getErrors() {
        return errors;
    }

    public RecognitionEvidence setErrors(List<String> errors) {
        this.errors = errors;
        return this;
    }
}

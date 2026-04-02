package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 人脸信息聚合接口响应。
 */
@Schema(description = "人脸信息聚合响应")
public class FaceInfoResponse {

    @Schema(description = "聚合后的人物主体信息")
    private PersonInfo person;

    @Schema(description = "候选人物相关的新闻列表")
    private List<NewsItem> news = new ArrayList<>();

    @JsonProperty("image_matches")
    @Schema(description = "来自搜图引擎的相似图片匹配结果")
    private List<ImageMatch> imageMatches = new ArrayList<>();

    @JsonProperty("facecheck_matches")
    @Schema(description = "来自 FaceCheck 的图片匹配结果")
    private List<FaceCheckMatch> facecheckMatches = new ArrayList<>();

    @Schema(description = "聚合过程中产生的非阻塞告警信息")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "接口处理状态，常见取值为 success、partial、failed", example = "success")
    private String status;

    @Schema(description = "请求失败时返回的错误说明，成功时通常为空", example = "外部服务暂时不可用")
    private String error;

    public PersonInfo getPerson() {
        return person;
    }

    public FaceInfoResponse setPerson(PersonInfo person) {
        this.person = person;
        return this;
    }

    public List<NewsItem> getNews() {
        return news;
    }

    public FaceInfoResponse setNews(List<NewsItem> news) {
        this.news = news;
        return this;
    }

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public FaceInfoResponse setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches;
        return this;
    }

    public List<FaceCheckMatch> getFacecheckMatches() {
        return facecheckMatches;
    }

    public FaceInfoResponse setFacecheckMatches(List<FaceCheckMatch> facecheckMatches) {
        this.facecheckMatches = facecheckMatches;
        return this;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public FaceInfoResponse setWarnings(List<String> warnings) {
        this.warnings = warnings;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public FaceInfoResponse setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getError() {
        return error;
    }

    public FaceInfoResponse setError(String error) {
        this.error = error;
        return this;
    }
}

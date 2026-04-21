package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "人脸信息聚合响应")
public class FaceInfoResponse {

    @Schema(description = "聚合后的人物主体信息")
    private PersonInfo person;

    @JsonProperty("image_matches")
    @Schema(description = "来自 Serper 搜图结果的图片匹配列表")
    private List<ImageMatch> imageMatches = new ArrayList<>();

    @JsonProperty("article_image_matches")
    @Schema(description = "供文章来源区完整展示的图片匹配列表，不受主图聚合折叠影响")
    private List<ImageMatch> articleImageMatches = new ArrayList<>();

    @Schema(description = "聚合过程中产生的非阻塞告警信息")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "多脸时的选脸工作流信息")
    private FaceSelectionPayload selection;

    @Schema(description = "接口处理状态，常见值为 success、partial、failed、selection_required", example = "success")
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

    public List<ImageMatch> getImageMatches() {
        return imageMatches;
    }

    public FaceInfoResponse setImageMatches(List<ImageMatch> imageMatches) {
        this.imageMatches = imageMatches == null ? new ArrayList<>() : imageMatches;
        return this;
    }

    public List<ImageMatch> getArticleImageMatches() {
        return articleImageMatches;
    }

    public FaceInfoResponse setArticleImageMatches(List<ImageMatch> articleImageMatches) {
        this.articleImageMatches = articleImageMatches == null ? new ArrayList<>() : articleImageMatches;
        return this;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public FaceInfoResponse setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
        return this;
    }

    public FaceSelectionPayload getSelection() {
        return selection;
    }

    public FaceInfoResponse setSelection(FaceSelectionPayload selection) {
        this.selection = selection;
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

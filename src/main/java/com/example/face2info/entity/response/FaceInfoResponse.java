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

    private PersonInfo person;
    private List<NewsItem> news = new ArrayList<>();

    @JsonProperty("image_matches")
    private List<ImageMatch> imageMatches = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();
    private String status;
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

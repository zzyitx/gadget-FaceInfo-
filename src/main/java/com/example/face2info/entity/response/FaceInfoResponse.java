package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "Face2Info response")
public class FaceInfoResponse {

    private PersonInfo person;
    private List<NewsItem> news = new ArrayList<>();

    @JsonProperty("image_matches")
    private List<ImageMatch> imageMatches = new ArrayList<>();

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

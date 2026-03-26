package com.example.face2info.entity.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SerpAPI visual match")
public class ImageMatch {

    private int position;
    private String title;
    private String link;
    private String source;

    public int getPosition() {
        return position;
    }

    public ImageMatch setPosition(int position) {
        this.position = position;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public ImageMatch setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getLink() {
        return link;
    }

    public ImageMatch setLink(String link) {
        this.link = link;
        return this;
    }

    public String getSource() {
        return source;
    }

    public ImageMatch setSource(String source) {
        this.source = source;
        return this;
    }
}

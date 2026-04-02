package com.example.face2info.entity.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "搜图引擎视觉匹配结果")
public class ImageMatch {

    @Schema(description = "结果在原始搜索列表中的位置", example = "1")
    private int position;

    @Schema(description = "匹配结果标题", example = "Jay Chou - Official Site")
    private String title;

    @Schema(description = "匹配结果链接地址", example = "https://example.com/person/jay-chou")
    private String link;

    @Schema(description = "结果来源站点或来源名称", example = "Wikipedia")
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

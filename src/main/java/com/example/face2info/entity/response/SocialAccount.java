package com.example.face2info.entity.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 社交账号 DTO。
 */
@Schema(description = "社交账号信息")
public class SocialAccount {

    @Schema(description = "社交平台名称", example = "Instagram")
    private String platform;

    @Schema(description = "社交账号主页链接", example = "https://instagram.com/example")
    private String url;

    @Schema(description = "社交平台上的用户名或账号标识", example = "jaychou")
    private String username;

    @Schema(description = "账号来源，例如 google 或 maigret", example = "maigret")
    private String source;

    @Schema(description = "是否为疑似账号。Maigret 仅按用户名查询，默认作为疑似结果展示", example = "true")
    private Boolean suspected;

    @Schema(description = "置信度标签", example = "suspected")
    private String confidence;

    public String getPlatform() {
        return platform;
    }

    public SocialAccount setPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public SocialAccount setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public SocialAccount setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getSource() {
        return source;
    }

    public SocialAccount setSource(String source) {
        this.source = source;
        return this;
    }

    public Boolean getSuspected() {
        return suspected;
    }

    public SocialAccount setSuspected(Boolean suspected) {
        this.suspected = suspected;
        return this;
    }

    public String getConfidence() {
        return confidence;
    }

    public SocialAccount setConfidence(String confidence) {
        this.confidence = confidence;
        return this;
    }
}

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
}

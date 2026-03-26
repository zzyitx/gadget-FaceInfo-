package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "社交账号信息")
/**
 * 社交账号 DTO。
 */
public class SocialAccount {

    private String platform;
    private String url;
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

package com.example.face2info.entity.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型解析后的人物信息。
 */
@Schema(description = "大模型解析后的人物画像")
public class ResolvedPersonProfile {

    @Schema(description = "解析后确认的人物名称")
    private String resolvedName;

    @Schema(description = "解析后的人物摘要")
    private String summary;

    @Schema(description = "解析出的关键事实")
    private List<String> keyFacts = new ArrayList<>();

    @Schema(description = "解析出的标签集合")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "支撑当前画像的证据链接")
    private List<String> evidenceUrls = new ArrayList<>();

    public String getResolvedName() {
        return resolvedName;
    }

    public ResolvedPersonProfile setResolvedName(String resolvedName) {
        this.resolvedName = resolvedName;
        return this;
    }

    public String getSummary() {
        return summary;
    }

    public ResolvedPersonProfile setSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public List<String> getKeyFacts() {
        return keyFacts;
    }

    public ResolvedPersonProfile setKeyFacts(List<String> keyFacts) {
        this.keyFacts = keyFacts;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public ResolvedPersonProfile setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public List<String> getEvidenceUrls() {
        return evidenceUrls;
    }

    public ResolvedPersonProfile setEvidenceUrls(List<String> evidenceUrls) {
        this.evidenceUrls = evidenceUrls;
        return this;
    }
}

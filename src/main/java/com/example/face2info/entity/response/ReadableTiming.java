package com.example.face2info.entity.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "流程时间和耗时")
public class ReadableTiming {

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("finished_at")
    private String finishedAt;

    @JsonProperty("duration_ms")
    private Long durationMs;

    @JsonProperty("duration_text")
    private String durationText;

    public String getStartedAt() {
        return startedAt;
    }

    public ReadableTiming setStartedAt(String startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public ReadableTiming setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
        return this;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public ReadableTiming setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public String getDurationText() {
        return durationText;
    }

    public ReadableTiming setDurationText(String durationText) {
        this.durationText = durationText;
        return this;
    }
}
